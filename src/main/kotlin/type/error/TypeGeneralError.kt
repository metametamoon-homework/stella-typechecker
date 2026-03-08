package type.error

import ast.Node
import ast.Var

data class TypeMismatch(
  override val errorNode: Node,
  val expectedType: type.Type,
  val actualType: type.Type,
) : TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION"
}

data class UndefinedVariable(override val errorNode: Var) : TypeError
