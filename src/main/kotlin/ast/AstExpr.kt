package ast

sealed interface Expr : Node

data class Succ(val expr: Expr, override val position: Position?) : Expr

data class IsZero(val arg: Expr, override val position: Position?) : Expr

data class Var(val name: String, override val position: Position?) : Expr

data class IntLiteral(val value: Int, override val position: Position?) : Expr

data class TrueLiteral(override val position: Position?) : Expr

data class FalseLiteral(override val position: Position?) : Expr

data class Application(val func: Expr, val args: List<Expr>, override val position: Position?) :
  Expr

data class IfExpression(
  val cond: Expr,
  val thenBranch: Expr,
  val elseBranch: Expr,
  override val position: Position?,
) : Expr

data class NatRec(val n: Expr, val init: Expr, val step: Expr, override val position: Position?) :
  Expr

data class Abstraction(
  val params: List<ParamDeclaration>,
  val returnExpr: Expr,
  override val position: Position?,
) : Expr

data class UnitConstant(override val position: Position?) : Expr

data class TupleLiteral(val projections: List<Expr>, override val position: Position?) : Expr

data class TupleDotExpression(
  val tupleExpr: Expr,
  val index: Int,
  override val position: Position?,
) : Expr

data class RecordLiteral(val bindings: Map<String, Expr>, override val position: Position?) : Expr

data class RecordDotExpression(
  val recordExpr: Expr,
  val label: String,
  override val position: Position?,
) : Expr
