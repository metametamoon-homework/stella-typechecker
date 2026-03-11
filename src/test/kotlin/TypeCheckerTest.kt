import ast.toAst
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapBoth
import generated.antlr.StellaLexer
import generated.antlr.StellaParser
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import type.error.prettyPrintError
import type.inferType

internal const val STELLA_EXTENSION = "stella"

class TypeCheckerTest {

  private val testsRoot = File("tests")

  @TestFactory
  fun typeCheckerTests(): List<DynamicTest> {
    val filterRegex = (System.getenv("STELLA_TESTS") ?: ".*").toRegex()
    return testsRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == STELLA_EXTENSION }
      .mapNotNull { sourceFile ->
        val path = sourceFile.relativeTo(testsRoot).path
        if (filterRegex.matches(path)) {
          val sourceRelativePath = path.removeSuffix(".$STELLA_EXTENSION")
          DynamicTest.dynamicTest(sourceRelativePath, sourceFile.toURI()) {
            typeCheckTest(sourceFile)
          }
        } else {
          null
        }
      }
      .toList()
  }

  private fun typeCheckTest(sourceFile: File) {
    val sourceText = sourceFile.readText()
    val textSpec =
      sourceFile
        .readLines()
        .dropWhile { "TEST_BEGIN" !in it }
        .drop(1)
        .takeWhile { "TEST_END" !in it }
        .joinToString("\n")
        .trim()
    val programSpec = Json.decodeFromString<TestDescription>(textSpec)

    val lexer = StellaLexer(CharStreams.fromString(sourceText))
    val program = StellaParser(CommonTokenStream(lexer)).program().toAst()
    val result = inferType(program)

    result.mapBoth(
      success = { println("Successfully typed!") },
      failure = { error ->
        val path = sourceFile.canonicalPath.toString()
        val message = error.prettyPrintError(path)
        println(message)
      },
    )
    when (programSpec) {
      is TestDescription.StopOnFirstError -> {
        val expectedError = programSpec.expectedError
        if (expectedError == null) {
          assertTrue(result.isOk)
        } else {
          val error = result.getError()
          assertNotNull(error)
          assertEquals(expectedError.code, error.coreError.errorId)
          val errorPosition = error.coreError.errorNode.position?.begin
          val locationRequired = expectedError.location
          if (locationRequired != null) {
            assertNotNull(errorPosition, "Error position is null")
            val oneBasedColumn = errorPosition.column + 1
            assertEquals("${errorPosition.row}:$oneBasedColumn", locationRequired)
          }
        }
      }
    }
  }
}
