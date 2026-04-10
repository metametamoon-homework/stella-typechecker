import java.io.File

private const val REFERENCE_COMMAND = "docker run -i fizruk/stella typecheck"
private val EXTENSION_HINT_REGEX = Regex("enable '(#.+)' extension")
private const val LANGUAGE_CORE_LINE = "language core;"
private const val EXTEND_WITH_PREFIX = "extend with "

fun main() {
  File("tests")
    .walkTopDown()
    .filter { it.isFile && it.extension == STELLA_EXTENSION }
    .forEach { sourceFile ->
      while (true) {
        println("begin $sourceFile")
        val output = runReferenceImplementation(sourceFile)
        val extension = EXTENSION_HINT_REGEX.find(output)?.groupValues?.get(1) ?: break
        println("Adding extension: $extension to file: $sourceFile")
        addExtension(sourceFile, extension)
      }
    }
}

private fun runReferenceImplementation(sourceFile: File): String {
  val process = ProcessBuilder("sh", "-c", REFERENCE_COMMAND).start()
  process.outputStream.bufferedWriter().use { it.write(sourceFile.readText()) }
  val stdout = process.inputStream.bufferedReader().use { it.readText() }
  val stderr = process.errorStream.bufferedReader().use { it.readText() }
  process.waitFor()
  return stdout + stderr
}

private fun addExtension(sourceFile: File, extension: String) {
  val lines = sourceFile.readLines().toMutableList()
  val languageIndex = lines.indexOf(LANGUAGE_CORE_LINE)
  val extendWithIndex = lines.indexOfFirst { it.startsWith(EXTEND_WITH_PREFIX) }
  if (extendWithIndex >= 0) {
    val currentExtensions =
      lines[extendWithIndex]
        .removePrefix(EXTEND_WITH_PREFIX)
        .removeSuffix(";")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    if (extension !in currentExtensions) {
      currentExtensions += extension
      lines[extendWithIndex] = EXTEND_WITH_PREFIX + currentExtensions.joinToString(", ") + ";"
    }
  } else {
    lines.add(languageIndex + 1, "")
    lines.add(languageIndex + 2, EXTEND_WITH_PREFIX + extension + ";")
  }
  sourceFile.writeText(lines.joinToString("\n"))
}
