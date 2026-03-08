package ast

data class Point(val column: Int, val row: Int)

data class Position(val begin: Point, val end: Point)

sealed interface Node {
  val position: Position?

  fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T
}

data class Program(val declarations: List<Declaration>, override val position: Position? = null) :
  Node {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitProgram(this, ctx)
}

data class ParamDeclaration(
  val name: String,
  val type: Type,
  override val position: Position? = null,
) : Node {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitParamDeclaration(this, ctx)
}

sealed interface Declaration : Node

data class FunDeclaration(
  val name: String,
  val type: Type,
  val paramDecls: List<ParamDeclaration>,
  override val position: Position? = null,
) : Declaration {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitFunParamDeclaration(this, ctx)
}

sealed interface Type : Node {

  data object Bool : Type {
    override val position: Position? = null

    override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
      visitor.visitTypeBool(this, ctx)
  }

  data object Nat : Type {
    override val position: Position? = null

    override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
      visitor.visitTypeNat(this, ctx)
  }

  data class Fun(
    val inputTypes: List<Type>,
    val returnType: Type,
    override val position: Position? = null,
  ) : Type {
    override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
      visitor.visitTypeFun(this, ctx)
  }
}

data class FunctionDeclaration(
  val name: String,
  val parameterDeclarations: List<ParamDeclaration>,
  val returnExpr: Expr,
  override val position: Position? = null,
) : Declaration {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitFunctionDeclaration(this, ctx)
}
