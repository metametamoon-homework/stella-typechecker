package type.error

import ast.Node

sealed interface TypeErrorFrame {
  data class WhileInferring(val node: Node) : TypeErrorFrame

  data class WhileTypeChecking(val node: Node, val type: type.Type) : TypeErrorFrame
}

class ContextualTypeError(val coreError: TypeError, val context: List<TypeErrorFrame>)

fun ContextualTypeError.withContextLayer(contextLayer: TypeErrorFrame): ContextualTypeError =
  ContextualTypeError(coreError, context + listOf(contextLayer))

fun TypeError.withEmptyContext(): ContextualTypeError = ContextualTypeError(this, emptyList())

fun ContextualTypeError.prettyPrintError(path: String): String = buildString {
  appendLine("e [${coreError.errorId}]")
  append("at file://$path:")
  val position = coreError.errorNode.position
  if (position != null) {
    append("${position.begin.row}:${position.begin.column + 1}:")
  }
  appendLine()
  appendLine(coreError.userFacingErrorDescription)
  for (frame in context) {
    when (frame) {
      is TypeErrorFrame.WhileInferring -> {
        val simpleName = frame.node.javaClass.simpleName
        val stringRangeOrNothing = frame.node.getStringRangeOrNothing()
        appendLine("- while inferring type of $simpleName $stringRangeOrNothing")
      }

      is TypeErrorFrame.WhileTypeChecking -> {
        val simpleName = frame.node.javaClass.simpleName
        val stringRangeOrNothing = frame.node.getStringRangeOrNothing()
        appendLine("- while checking type of $simpleName $stringRangeOrNothing is ${frame.type}")
      }
    }
  }
}

private fun Node.getStringRangeOrNothing(): String {
  val pos = position
  return if (pos != null) {
    "(${pos.begin.row}:${pos.begin.column + 1}-${pos.end.row}:${pos.end.column + 1})"
  } else {
    ""
  }
}
