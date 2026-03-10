package type.error

import ast.Node
import ast.Var

data class TypeMismatch(
  override val errorNode: Node,
  private val expectedType: type.Type,
  private val actualType: type.Type,
) : TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION"
  override val userFacingErrorDescription: String
    get() = "Expected type\n\t$expectedType\nwith actual type\n\t$actualType"
}

data class UndefinedVariable(override val errorNode: Var) : TypeError {
  override val errorId: String = "ERROR_UNDEFINED_VARIABLE"
  override val userFacingErrorDescription: String
    get() = "use of undefined identifier ${errorNode.name}"
}
