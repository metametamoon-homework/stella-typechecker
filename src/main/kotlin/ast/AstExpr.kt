package ast

sealed interface Expr : Node

data class Succ(val expr: Expr, override val position: Position? = null) : Expr {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitSucc(this, ctx)
}

data class Var(val name: String, override val position: Position? = null) : Expr {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitVar(this, ctx)
}

data class TrueLiteral(override val position: Position? = null) : Expr {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitTrueLiteral(this, ctx)
}

data class FalseLiteral(override val position: Position? = null) : Expr {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitFalseLiteral(this, ctx)
}

data class Application(
  val func: Expr,
  val args: List<Expr>,
  override val position: Position? = null,
) : Expr {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitApplication(this, ctx)
}

data class IfExpression(
  val cond: Expr,
  val thenBranch: Expr,
  val elseBranch: Expr,
  override val position: Position? = null,
) : Expr {
  override fun <Ctx, T> accept(visitor: AstVisitor<Ctx, T>, ctx: Ctx): T =
    visitor.visitIfExpr(this, ctx)
}
