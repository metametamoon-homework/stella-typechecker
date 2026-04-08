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
          testBody(sourceFile)
        }
      } else {
        null
      }
    }
    .toList()

internal fun extractTestDescription(sourceFile: File): TestDescription {
  val textSpec =
    sourceFile
      .readLines()
      .dropWhile { "TEST_BEGIN" !in it }
      .drop(1)
      .takeWhile { "TEST_END" !in it }
      .joinToString("\n")
      .trim()
  val programSpec = Json.decodeFromString<TestDescription>(textSpec)
  return programSpec
}
