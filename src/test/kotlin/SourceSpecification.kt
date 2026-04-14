import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ExpectedError(val code: String, val location: String)

@Serializable
sealed interface TestDescription {
  @Serializable
  @SerialName("stop-on-first-error")
  data class StopOnFirstError(@SerialName("expected-error") val expectedError: ExpectedError?) :
    TestDescription
}

@Serializable
data class SourceSpecification(val source: String? = null, val testDescription: TestDescription)
