import ast.toAst
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapBoth
import com.strumenta.antlrkotlin.parsers.generated.StellaLexer
import com.strumenta.antlrkotlin.parsers.generated.StellaParser
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import type.TypeCheckVisitor
import type.TypeInferenceCtx
import type.TypeInferenceVisitor
import type.emptyEnv
import type.error.prettyPrintError

internal const val STELLA_EXTENSION = "st"

class TypeCheckerTest {

  private val testsRoot = File(javaClass.getResource("tests")!!.toURI())

  @TestFactory
  fun typeCheckerTests(): List<DynamicTest> =
    testsRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "st" }
      .map { sourceFile ->
        val sourceRelativePath =
          sourceFile.relativeTo(testsRoot).path.removeSuffix(".$STELLA_EXTENSION")
        DynamicTest.dynamicTest(sourceRelativePath) { typeCheckTest(sourceFile) }
      }
      .toList()

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
    val result =
      program.accept(TypeInferenceVisitor(), TypeInferenceCtx(emptyEnv, TypeCheckVisitor()))

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
        }
      }
    }
  }
}
