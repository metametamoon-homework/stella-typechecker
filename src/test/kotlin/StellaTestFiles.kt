import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest

internal const val STELLA_EXTENSION = "stella"

internal fun stellaTests(
  testsRoot: File = File("tests"),
  filterRegex: Regex = (System.getenv("STELLA_TESTS") ?: ".*").toRegex(),
  testBody: (File) -> Unit,
): List<DynamicTest> =
  testsRoot
    .walkTopDown()
    .filter { it.isFile && it.extension == STELLA_EXTENSION }
    .mapNotNull { sourceFile ->
      val path = sourceFile.relativeTo(testsRoot).path
      if (filterRegex.matches(path)) {
        val sourceRelativePath = path.removeSuffix(".$STELLA_EXTENSION")
        DynamicTest.dynamicTest(sourceRelativePath, sourceFile.toURI()) {
          println("Test name is $sourceRelativePath")
          val regex = sourceRelativePath.replace("/", ".").replace("-", ".").plus(".*")
          println("Test regex is $regex")
          testBody(sourceFile)
        }
      } else {
        null
      }
    }
    .toList()

private val ERROR_COMMENT_REGEX = Regex("""/\*e=(ERROR_\w+)\*/""")

internal fun extractTestDescription(sourceFile: File): TestDescription {
  val lines = sourceFile.readLines()

  if (lines.any { "TEST_BEGIN" in it }) {
    val textSpec =
      lines
        .dropWhile { "TEST_BEGIN" !in it }
        .drop(1)
        .takeWhile { "TEST_END" !in it }
        .joinToString("\n")
        .trim()
    return Json.decodeFromString<TestDescription>(textSpec)
  }

  for ((index, line) in lines.withIndex()) {
    val match = ERROR_COMMENT_REGEX.find(line) ?: continue
    val errorCode = match.groupValues[1]
    val row = index + 1
    val afterComment = line.substring(match.range.last + 1)
    val col = match.range.last + 2 + (afterComment.length - afterComment.trimStart().length)
    return TestDescription.StopOnFirstError(ExpectedError(errorCode, "$row:$col"))
  }

  if (lines.any { "SUCCESS" in it }) {
    return TestDescription.StopOnFirstError(null)
  }

  error("Cannot determine test description for ${sourceFile.name}")
}
