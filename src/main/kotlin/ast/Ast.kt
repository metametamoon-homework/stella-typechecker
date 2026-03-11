package ast

data class Point(val column: Int, val row: Int)

data class Position(val begin: Point, val end: Point)

sealed interface Node {
  val position: Position?
}

data class Program(val declarations: List<Declaration>, override val position: Position? = null) :
  Node

data class ParamDeclaration(
  val name: String,
  val type: Type,
  override val position: Position? = null,
) : Node

sealed interface Declaration : Node

data class FunDeclaration(
  val name: String,
  val type: Type,
  val paramDecls: List<ParamDeclaration>,
  override val position: Position? = null,
) : Declaration

sealed interface Type : Node {

  data object Bool : Type {
    override val position: Position? = null
  }

  data object Unit : Type {
    override val position: Position? = null
  }

  data object Nat : Type {
    override val position: Position? = null
  }

  data class Fun(
    val inputTypes: List<Type>,
    val returnType: Type,
    override val position: Position? = null,
  ) : Type
}

data class FunctionDeclaration(
  val name: String,
  val parameterDeclarations: List<ParamDeclaration>,
  val returnType: Type?,
  val returnExpr: Expr,
  override val position: Position? = null,
) : Declaration
