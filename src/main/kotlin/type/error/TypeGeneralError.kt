package type.error

import ast.Abstraction
import ast.Expr
import ast.Node
import ast.Var
import type.Type

data class TypeMismatch(
  override val errorNode: Node,
  private val expectedType: type.Type,
  private val actualType: type.Type,
) : TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION"
  override val userFacingErrorDescription: String
    get() =
      "Expected type\n\t${expectedType.prettyPrint()}\nwith actual type\n\t${actualType.prettyPrint()}"
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

data class UnexpectedRecordField(override val errorNode: Expr, private val label: String) :
  TypeError {
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

data class NotAList(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_NOT_A_LIST"
}

data class UnexpectedList(override val errorNode: Expr, private val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_LIST"
  override val userFacingErrorDescription: String
    get() = "found a list where type ${expectedType.prettyPrint()} was expected"
}

data class AmbiguousList(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_AMBIGUOUS_LIST"
}

data class UnexpectedVariantLabel(override val errorNode: Expr, private val label: String) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_VARIANT_LABEL"
  override val userFacingErrorDescription: String
    get() = "variant type does not have label '$label'"
}

data class NotAVariantType(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION"
}

data class AmbiguousVariantType(override val errorNode: Expr) : TypeError {
  override val errorId: String = "ERROR_AMBIGUOUS_VARIANT_TYPE"
}

data class UnexpectedLambda(override val errorNode: Abstraction, private val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_LAMBDA"
  override val userFacingErrorDescription: String
    get() = "found a lambda where type ${expectedType.prettyPrint()} was expected"
}

data class UnexpectedTuple(override val errorNode: Expr, private val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TUPLE"
  override val userFacingErrorDescription: String
    get() = "found a tuple where type ${expectedType.prettyPrint()} was expected"
}

data class UnexpectedRecord(override val errorNode: Expr, private val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_RECORD"
  override val userFacingErrorDescription: String
    get() = "found a record where type ${expectedType.prettyPrint()} was expected"
}

data class UnexpectedVariant(override val errorNode: Expr, private val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_VARIANT"
  override val userFacingErrorDescription: String
    get() = "found a variant where type ${expectedType.prettyPrint()} was expected"
}

data class UnexpectedInjection(override val errorNode: Expr, private val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_INJECTION"
  override val userFacingErrorDescription: String
    get() = "found an injection where type ${expectedType.prettyPrint()} was expected"
}

data class UnexpectedLambdaParameterType(override val errorNode: Node, val expectedType: Type) :
  TypeError {
  override val errorId: String = "ERROR_UNEXPECTED_TYPE_FOR_PARAMETER"
}
