package type.error

import ast.Node

sealed interface TypeError {
  val errorId: String
    get() = javaClass.simpleName

  val userFacingErrorDescription: String

  val errorNode: Node
}
