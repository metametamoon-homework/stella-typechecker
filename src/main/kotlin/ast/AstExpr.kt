package ast

sealed interface Expr : Node

data class Succ(val expr: Expr, override val position: Position? = null) : Expr

data class IsZero(val arg: Expr, override val position: Position? = null) : Expr

data class Var(val name: String, override val position: Position? = null) : Expr

data class TrueLiteral(override val position: Position? = null) : Expr

data class FalseLiteral(override val position: Position? = null) : Expr

data class Application(
  val func: Expr,
  val args: List<Expr>,
  override val position: Position? = null,
) : Expr

data class IfExpression(
  val cond: Expr,
  val thenBranch: Expr,
  val elseBranch: Expr,
  override val position: Position? = null,
) : Expr
