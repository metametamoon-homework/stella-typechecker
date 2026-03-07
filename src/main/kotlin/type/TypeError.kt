package type

import ast.Node
import ast.Succ
import ast.Var

sealed interface TypeError {
  val errorId: String
    get() = this.javaClass.simpleName
}

sealed interface TypeInferenceError : TypeError

class TypeInferenceErrorWrapper(val underlying: TypeInferenceError, val wrappingNode: Node) :
  TypeInferenceError {
  override val errorId: String
    get() = underlying.errorId
}

class SuccInferenceError(val succ: Succ) : TypeInferenceError

class VarInferenceError(val variable: Var) : TypeInferenceError
