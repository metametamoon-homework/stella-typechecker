@file:Suppress("UnsafeCallOnNullableType")

package ast

import generated.antlr.StellaParser
import org.antlr.v4.kotlinruntime.ParserRuleContext

private fun ParserRuleContext.toPosition(): Position {
  val start = start ?: error("")
  val stop = stop ?: error("")
  return Position(
    begin = Point(column = start.charPositionInLine, row = start.line),
    end = Point(column = stop.charPositionInLine + (stop.text?.length ?: 1), row = stop.line),
  )
}

fun StellaParser.ProgramContext.toAst(): Program {
  return Program(
    declarations = decls.map { it.toAst() },
    extensions =
      this.extension().flatMap {
        (it as StellaParser.AnExtensionContext).ExtensionName().map { singleExtensionName ->
          singleExtensionName.text
        }
      },
    position = toPosition(),
  )
}

fun StellaParser.DeclContext.toAst(): Declaration =
  when (this) {
    is StellaParser.DeclFunContext ->
      FunctionDeclaration(
        name = this.name?.text ?: "<unknown>",
        parameterDeclarations = this.paramDecls.map { it.toAst() },
        returnType = this.returnType!!.toAst(),
        localDeclarations = this.localDecls.map { it.toAst() },
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
    is StellaParser.PredContext -> Pred(this.n!!.toAst(), toPosition())
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
      RecordLiteral(this.bindings.map { it.name?.text!! to it.rhs!!.toAst() }, toPosition())
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
    is StellaParser.InlContext -> Inl(expr = this.expr_!!.toAst(), position = toPosition())
    is StellaParser.InrContext -> Inr(expr = this.expr_!!.toAst(), position = toPosition())
    is StellaParser.ListContext ->
      ListLiteral(elements = this.exprs.map { it.toAst() }, position = toPosition())
    is StellaParser.ConsListContext ->
      ConsList(head = this.head!!.toAst(), tail = this.tail!!.toAst(), position = toPosition())
    is StellaParser.HeadContext -> ListHead(list = this.list!!.toAst(), position = toPosition())
    is StellaParser.TailContext -> ListTail(list = this.list!!.toAst(), position = toPosition())
    is StellaParser.IsEmptyContext ->
      ListIsEmpty(list = this.list!!.toAst(), position = toPosition())
    is StellaParser.MatchContext ->
      Match(
        scrutinee = this.expr_!!.toAst(),
        cases = this.cases.map { it.toAst() },
        position = toPosition(),
      )
    is StellaParser.VariantContext ->
      VariantLiteral(
        label = this.label!!.text!!,
        expr = this.rhs!!.toAst(),
        position = toPosition(),
      )
    is StellaParser.FixContext -> Fix(expr = this.expr_!!.toAst(), position = toPosition())
    is StellaParser.ParenthesisedExprContext -> this.expr_!!.toAst()
    is StellaParser.TerminatingSemicolonContext -> this.expr_!!.toAst()
    else -> error("Unsupported expression: ${this::class.simpleName}")
  }

fun StellaParser.PatternBindingContext.toAst(): Binding =
  Binding(pattern = pat!!.toAst(), expr = rhs!!.toAst())

fun StellaParser.MatchCaseContext.toAst(): MatchCase =
  MatchCase(pattern = pattern_!!.toAst(), expr = expr_!!.toAst())

fun StellaParser.PatternContext.toAst(): Pattern =
  when (this) {
    is StellaParser.PatternVarContext ->
      Pattern.Variable(name = this.name?.text ?: "<unknown>", position = toPosition())
    is StellaParser.PatternInlContext ->
      Pattern.Inl(inner = this.pattern_!!.toAst(), position = toPosition())
    is StellaParser.PatternInrContext ->
      Pattern.Inr(inner = this.pattern_!!.toAst(), position = toPosition())
    is StellaParser.PatternVariantContext ->
      Pattern.Variant(
        label = this.label?.text ?: "<unknown>",
        inner = this.pattern_?.toAst(),
        position = toPosition(),
      )
    is StellaParser.PatternCastAsContext ->
      Pattern.CastAs(
        inner = this.pattern_!!.toAst(),
        type = this.type_!!.toAst(),
        position = toPosition(),
      )
    else -> error("Unsupported pattern: ${this::class.simpleName}")
  }

@Suppress("CyclomaticComplexMethod")
fun StellaParser.StellatypeContext.toAst(): Type =
  when (this) {
    is StellaParser.TypeNatContext -> Type.Nat(toPosition())
    is StellaParser.TypeBoolContext -> Type.Bool(toPosition())
    is StellaParser.TypeFunContext ->
      Type.Fun(
        inputTypes = this.paramTypes.map { it.toAst() },
        returnType = this.returnType!!.toAst(),
        position = toPosition(),
      )
    is StellaParser.TypeUnitContext -> Type.Unit(toPosition())
    is StellaParser.TypeTupleContext -> Type.Tuple(this.types.map { it.toAst() }, toPosition())
    is StellaParser.TypeRecordContext ->
      Type.Record(this.fieldTypes.map { it.label?.text!! to it.type_!!.toAst() }, toPosition())
    is StellaParser.TypeSumContext ->
      Type.Sum(left = this.left!!.toAst(), right = this.right!!.toAst(), position = toPosition())
    is StellaParser.TypeListContext ->
      Type.ListType(elementType = this.type_!!.toAst(), position = toPosition())
    is StellaParser.TypeVariantContext ->
      Type.Variant(
        fields =
          this.fieldTypes.map {
            Type.VariantFieldType(label = it.label?.text!!, type = it.type_!!.toAst())
          },
        position = toPosition(),
      )
    is StellaParser.TypeParensContext -> this.type_!!.toAst()
    is StellaParser.TypeRefContext -> Type.Ref(this.type_!!.toAst(), position = toPosition())
    is StellaParser.TypeAutoContext -> Type.Auto(position = toPosition())
    else -> error("Unsupported type: ${this::class.simpleName} at position ${toPosition()}")
  }
