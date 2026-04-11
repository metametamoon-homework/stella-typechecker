import ast.toAst
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.michaelbull.result.mapBoth
import generated.antlr.StellaLexer
import generated.antlr.StellaParser
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.system.exitProcess
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import type.TypeChecker
import type.emptyEnv
import type.error.prettyPrintError

private const val TEMP_FILE_PREFIX = "stella-stdin-"
private const val TEMP_FILE_SUFFIX = ".stella"

private class TypeCheckerCli : CliktCommand() {
  private val inputFile by
    option("--file", help = "Read Stella code from this file instead of standard input.")
      .file(mustExist = true, canBeDir = false)

  override fun run() {
    val sourceFile = inputFile ?: readStandardInputToTemporaryFile()
    exitProcess(typeCheckFile(sourceFile))
  }
}

private fun readStandardInputToTemporaryFile(): File {
  val stdinSource = System.`in`.bufferedReader().use { it.readText() }
  return createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX).toFile().apply {
    writeText(stdinSource)
  }
}

private fun typeCheckFile(sourceFile: File): Int {
  val sourceText = sourceFile.readText()
  val lexer = StellaLexer(CharStreams.fromString(sourceText))
  val program = StellaParser(CommonTokenStream(lexer)).program().toAst()
  val result = TypeChecker(program.extensions).inferType(program, emptyEnv)

  return result.mapBoth(
    success = {
      println("Successfully typed!")
      0
    },
    failure = { error ->
      System.err.println(error.prettyPrintError(sourceFile.canonicalPath))
      1
    },
  )
}

fun main(args: Array<String>): Unit = TypeCheckerCli().main(args)
