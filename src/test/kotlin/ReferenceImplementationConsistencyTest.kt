import java.io.File
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

private const val REFERENCE_COMMAND = "docker run -i fizruk/stella typecheck"
private val REFERENCE_ERROR_REGEX = Regex("ERROR_[A-Z0-9_]+")

// @Disabled
class ReferenceImplementationConsistencyTest {
  @TestFactory
  fun referenceImplementationTests(): List<DynamicTest> =
    stellaTests(filterRegex = "lists.list.amb.*".toRegex(), testBody = ::referenceImplementationTest)

  private fun referenceImplementationTest(sourceFile: File) {
    println("Running tests on ${sourceFile.toURI()}")
    val expectedCode =
      when (val programSpec = extractTestDescription(sourceFile)) {
        is TestDescription.StopOnFirstError -> programSpec.expectedError?.code
      }

    val process = ProcessBuilder("sh", "-c", REFERENCE_COMMAND).start()
    process.outputStream.bufferedWriter().use {
      it.write(sourceFile.readText())
      it.flush()
    }

    val output = process.errorStream.bufferedReader().use { reader -> reader.readText() }
    val output2 = process.inputStream.bufferedReader().use { reader -> reader.readText() }
    println(output)
    println("middle")
    println(output2)

    val actualPossibleCodes =
      REFERENCE_ERROR_REGEX.findAll(output2).toList().map { it.value }
    process.waitFor()

    if (expectedCode == null) {
      assert(actualPossibleCodes.isEmpty())
    } else {
      assert(expectedCode in actualPossibleCodes)
    }
  }
}
