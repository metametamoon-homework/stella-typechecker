package ast

import type.TypeVar

sealed interface Expr : Node

data class Succ(val expr: Expr, override val position: Position) : Expr

data class IsZero(val arg: Expr, override val position: Position) : Expr

data class Pred(val arg: Expr, override val position: Position) : Expr

data class Var(val name: String, override val position: Position) : Expr

data class IntLiteral(val value: Int, override val position: Position) : Expr

data class TrueLiteral(override val position: Position) : Expr

data class FalseLiteral(override val position: Position) : Expr

data class Application(val func: Expr, val args: List<Expr>, override val position: Position) : Expr

data class IfExpression(
  val cond: Expr,
  val thenBranch: Expr,
  val elseBranch: Expr,
  override val position: Position,
) : Expr

data class NatRec(val n: Expr, val init: Expr, val step: Expr, override val position: Position) :
  Expr

data class Abstraction(
  val params: List<ParamDeclaration>,
  val returnExpr: Expr,
  override val position: Position,
) : Expr

data class TupleLiteral(val projections: List<Expr>, override val position: Position) : Expr

data class TupleDotExpression(
  val tupleExpr: Expr,
  val index: Int,
  override val position: Position,
) : Expr

data class RecordLiteral(val bindings: List<Pair<String, Expr>>, override val position: Position) :
  Expr

data class RecordDotExpression(
  val recordExpr: Expr,
  val label: String,
  override val position: Position,
) : Expr

data class Inl(val expr: Expr, override val position: Position) : Expr

data class Inr(val expr: Expr, override val position: Position) : Expr

data class ListLiteral(val elements: List<Expr>, override val position: Position) : Expr

data class ConsList(val head: Expr, val tail: Expr, override val position: Position) : Expr

data class ListHead(val list: Expr, override val position: Position) : Expr

data class ListTail(val list: Expr, override val position: Position) : Expr

data class ListIsEmpty(val list: Expr, override val position: Position) : Expr

data class Fix(val expr: Expr, override val position: Position) : Expr

data class MatchCase(val pattern: Pattern, val expr: Expr)

data class Match(val scrutinee: Expr, val cases: List<MatchCase>, override val position: Position) :
  Expr

data class VariantLiteral(val label: String, val expr: Expr, override val position: Position) : Expr

sealed interface Pattern : Expr {
  data class Variable(val name: String, override val position: Position) : Pattern

  data class Inl(val inner: Pattern, override val position: Position) : Pattern

  data class Inr(val inner: Pattern, override val position: Position) : Pattern

  data class Variant(val label: String, val inner: Pattern?, override val position: Position) :
    Pattern
}

data class Binding(val pattern: Pattern, val expr: Expr)

data class TypeAscription(val expr: Expr, val type: Type, override val position: Position) : Expr

data class LetBinding(
  val bindings: List<Binding>,
  val body: Expr,
  override val position: Position,
) : Expr

data class UnitConst(override val position: Position) : Expr

data class TypeApplication(val func: Expr, val args: List<Type>, override val position: Position) :
  Expr

data class TypeAbstraction(
  val typeArgs: List<Type.TypeVar>,
  val body: Expr,
  override val position: Position,
) : Expr
