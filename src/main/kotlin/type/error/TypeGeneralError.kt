package type.error

import ast.Expr
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

data class NotAFunction(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_NOT_A_FUNCTION"
}

data class NotATuple(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_NOT_A_TUPLE"
}

data class TupleIndexOutOfBound(override val errorNode: Expr) : TypeError {
  override val errorId: String = "TUPLE_INDEX_OUT_OF_BOUND"
}

data class NotARecord(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_NOT_A_RECORD"
}

data class UnexpectedRecordField(override val errorNode: Expr, val label: String) : TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_RECORD_FIELD"
  override val userFacingErrorDescription: String
    get() = "record does not have field '$label'"
}

data class AmbiguousSumType(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_AMBIGUOUS_SUM_TYPE"
}

data class NotASumType(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION"
}

data class NonExhaustivePatternMatching(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_NONEXHAUSTIVE_MATCH_PATTERNS"
}

data class MissingMain(override val errorNode: Node) : TypeError {
  override val errorId: String = "ERROR_MISSING_MAIN"
}
