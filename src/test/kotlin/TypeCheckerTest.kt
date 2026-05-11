import ast.toAst
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapBoth
import generated.antlr.StellaLexer
import generated.antlr.StellaParser
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import type.TypeChecker
import type.emptyEnv
import type.error.prettyPrintError

class TypeCheckerTest {

  //  @Disabled("Not implemented yet")
  @TestFactory fun typeCheckerTests(): List<DynamicTest> = stellaTests(testBody = ::typeCheckTest)

  private fun typeCheckTest(sourceFile: File) {
    val sourceText = sourceFile.readText()
    println("Performing analysis of ${sourceFile.toURI()}\n")
    val programSpec = extractTestDescription(sourceFile)

    val lexer = StellaLexer(CharStreams.fromString(sourceText))
    val program = StellaParser(CommonTokenStream(lexer)).program().toAst()
    val result = TypeChecker(program.extensions).inferType(program, emptyEnv)

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
          assertTrue(
            result.isOk,
            "Found typecheck error in a correct program: ${result.getError()}",
          )
        } else {
          val error = result.getError()
          assertNotNull(error)
          assertEquals(expectedError.code, error.coreError.errorId)
          val errorPosition = error.coreError.errorNode.position.begin
          val expectedErrorLocation = expectedError.location
          assertNotNull(errorPosition, "Error position is null")
          val oneBasedColumn = errorPosition.column + 1
          assertEquals("${errorPosition.row}:$oneBasedColumn", expectedErrorLocation)
        }
      }
    }
  }
}
