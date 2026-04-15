package ast

data class Point(val column: Int, val row: Int)

data class Position(val begin: Point, val end: Point)

sealed interface Node {
  val position: Position
}

data class Program(
  val declarations: List<Declaration>,
  override val position: Position,
  val extensions: List<String>,
) : Node {
  init {
    println("Extensions are: $extensions")
  }
}

data class ParamDeclaration(val name: String, val type: Type, override val position: Position) :
  Node

sealed interface Declaration : Node

sealed interface Type : Node {

  data class Bool(override val position: Position) : Type

  data class Unit(override val position: Position) : Type

  data class Nat(override val position: Position) : Type

  data class Fun(
    val inputTypes: List<Type>,
    val returnType: Type,
    override val position: Position,
  ) : Type

  data class Ref(val inner: Type, override val position: Position) : Type

  data class Tuple(val projections: List<Type>, override val position: Position) : Type

  data class Record(val projections: List<Pair<String, Type>>, override val position: Position) :
    Type

  data class Sum(val left: Type, val right: Type, override val position: Position) : Type

  data class ListType(val elementType: Type, override val position: Position) : Type

  data class VariantFieldType(val label: String, val type: Type)

  data class Variant(val fields: List<VariantFieldType>, override val position: Position) : Type

  data class Top(override val position: Position) : Type

  data class Bottom(override val position: Position) : Type
}

data class FunctionDeclaration(
  val name: String,
  val parameterDeclarations: List<ParamDeclaration>,
  val returnType: Type,
  val localDeclarations: List<Declaration> = emptyList(),
  val returnExpr: Expr,
  override val position: Position,
) : Declaration

sealed interface ExceptionInfoDeclaration : Declaration

data class ExceptionTypeDeclaration(val type: ast.Type, override val position: Position) :
  ExceptionInfoDeclaration

data class ExceptionVariantDeclaration(
  val label: String,
  val type: ast.Type,
  override val position: Position,
) : ExceptionInfoDeclaration
