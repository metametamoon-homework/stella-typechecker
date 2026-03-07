@file:Suppress("UnsafeCallOnNullableType")

package ast

import com.strumenta.antlrkotlin.parsers.generated.StellaParser
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
        declarations = this.localDecls.map { it.toAst() },
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
    else -> error("Unsupported expression: ${this::class.simpleName}")
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
    else -> error("Unsupported type: ${this::class.simpleName}")
  }
