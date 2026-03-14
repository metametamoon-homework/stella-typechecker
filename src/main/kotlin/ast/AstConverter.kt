@file:Suppress("UnsafeCallOnNullableType")

package ast

import generated.antlr.StellaParser
import org.antlr.v4.kotlinruntime.ParserRuleContext

private fun ParserRuleContext.toPosition(): Position? {
  val start = start ?: return null
  val stop = stop ?: return null
  return Position(
    begin = Point(column = start.charPositionInLine, row = start.line),
    end = Point(column = stop.charPositionInLine + (stop.text?.length ?: 1), row = stop.line),
  )
}

fun StellaParser.ProgramContext.toAst(): Program =
  Program(declarations = decls.map { it.toAst() }, position = toPosition())

fun StellaParser.DeclContext.toAst(): Declaration =
  when (this) {
    is StellaParser.DeclFunContext ->
      FunctionDeclaration(
        name = this.name?.text ?: "<unknown>",
        parameterDeclarations = this.paramDecls.map { it.toAst() },
        returnType = this.returnType?.toAst(),
        //        localDeclarations = this.localDecls.map { it.toAst() },
        returnExpr = this.returnExpr!!.toAst(),
        position = toPosition(),
      )
    else -> error("Unsupported declaration: ${this::class.simpleName}")
  }

fun StellaParser.ParamDeclContext.toAst(): ParamDeclaration {
  return ParamDeclaration(
    name = name?.text ?: "<unknown>",
    type = paramType!!.toAst(),
    position = toPosition(),
  )
}

// a gigantic when is going to be complex, but there is no work around it
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun StellaParser.ExprContext.toAst(): Expr =
  when (this) {
    is StellaParser.SuccContext -> Succ(expr = this.n!!.toAst(), position = toPosition())
    is StellaParser.VarContext ->
      Var(name = this.name?.text ?: "<unknown>", position = toPosition())
    is StellaParser.ApplicationContext ->
      Application(
        func = this.func!!.toAst(),
        args = this.args.map { it.toAst() },
        position = toPosition(),
      )
    is StellaParser.ConstTrueContext -> TrueLiteral(toPosition())
    is StellaParser.ConstFalseContext -> FalseLiteral(toPosition())
    is StellaParser.IfContext ->
      IfExpression(
        this.condition!!.toAst(),
        this.thenExpr!!.toAst(),
        this.elseExpr!!.toAst(),
        toPosition(),
      )
    is StellaParser.IsZeroContext -> IsZero(this.n!!.toAst(), toPosition())
    is StellaParser.NatRecContext ->
      NatRec(this.n!!.toAst(), this.initial!!.toAst(), this.step!!.toAst(), toPosition())
    is StellaParser.ConstIntContext -> IntLiteral(this.n?.text?.toIntOrNull()!!, toPosition())
    is StellaParser.AbstractionContext ->
      Abstraction(this.paramDecls.map { it.toAst() }, this.returnExpr!!.toAst(), toPosition())
    is StellaParser.ConstUnitContext -> UnitConstant(toPosition())
    is StellaParser.TupleContext -> TupleLiteral(this.exprs.map { it.toAst() }, toPosition())
    is StellaParser.DotTupleContext ->
      TupleDotExpression(this.expr_!!.toAst(), this.index?.text!!.toInt(), toPosition())
    is StellaParser.RecordContext ->
      RecordLiteral(this.bindings.associate { it.name?.text!! to it.rhs!!.toAst() }, toPosition())
    is StellaParser.DotRecordContext ->
      RecordDotExpression(this.expr_!!.toAst(), this.label?.text!!, toPosition())
    is StellaParser.TypeAscContext ->
      TypeAscription(
        expr = this.expr_!!.toAst(),
        type = this.type_!!.toAst(),
        position = toPosition(),
      )
    is StellaParser.LetContext ->
      LetBinding(
        bindings = this.patternBindings.map { it.toAst() },
        body = this.body!!.toAst(),
        position = toPosition(),
      )
    else -> error("Unsupported expression: ${this::class.simpleName}")
  }

fun StellaParser.PatternBindingContext.toAst(): Binding =
  Binding(pattern = this.pat!!.toAst(), expr = this.rhs!!.toAst())

fun StellaParser.PatternContext.toAst(): Pattern =
  when (this) {
    is StellaParser.PatternVarContext ->
      Pattern.Variable(name = this.name?.text ?: "<unknown>", position = toPosition())
    else -> error("Unsupported pattern: ${this::class.simpleName}")
  }

fun StellaParser.StellatypeContext.toAst(): Type =
  when (this) {
    is StellaParser.TypeNatContext -> Type.Nat
    is StellaParser.TypeBoolContext -> Type.Bool
    is StellaParser.TypeFunContext ->
      Type.Fun(
        inputTypes = this.paramTypes.map { it.toAst() },
        returnType = this.returnType!!.toAst(),
        position = toPosition(),
      )
    is StellaParser.TypeUnitContext -> Type.Unit
    is StellaParser.TypeTupleContext -> Type.Tuple(this.types.map { it.toAst() })
    is StellaParser.TypeRecordContext ->
      Type.Record(this.fieldTypes.associate { it.label?.text!! to it.type_!!.toAst() })
    else -> error("Unsupported type: ${this::class.simpleName} at position ${toPosition()}")
  }
