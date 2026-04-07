package type.error

import ast.Node

sealed interface TypeError {
  val errorId: String
    get() = javaClass.simpleName

  val userFacingErrorDescription: String
  //    get() = "NOT YET DOCUMENTED (${javaClass.simpleName})"

  val errorNode: Node
}
