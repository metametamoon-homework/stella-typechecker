package type

import ast.Application
import ast.AstVisitor
import ast.FalseLiteral
import ast.FunDeclaration
import ast.FunctionDeclaration
import ast.IfExpression
import ast.ParamDeclaration
import ast.Program
import ast.Succ
import ast.TrueLiteral
import ast.Var
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import type.error.ContextualTypeError
import type.error.TypeMismatch
import type.error.UndefinedVariable
import type.error.withEmptyContext
import utils.raise

data class TypeCheckCtx(
  val env: Env,
  val expectedType: Type,
  val typeInferenceVisitor: TypeInferenceVisitor,
)

@Suppress("TooManyFunctions")
class TypeCheckVisitor : AstVisitor<TypeCheckCtx, Result<Unit, ContextualTypeError>> {
  override fun visitSucc(succ: Succ, ctx: TypeCheckCtx): Result<Unit, ContextualTypeError> =
    binding {
      if (ctx.expectedType != Nat) {
        raise(TypeMismatch(succ, ctx.expectedType, Nat).withEmptyContext())
      }
      succ.expr.accept(this@TypeCheckVisitor, ctx).bind()
    }

  override fun visitVar(varExpr: Var, ctx: TypeCheckCtx): Result<Unit, ContextualTypeError> =
    binding {
      val actual = ctx.env[varExpr.name] ?: raise(UndefinedVariable(varExpr).withEmptyContext())
      if (actual != ctx.expectedType) {
        raise(TypeMismatch(varExpr, ctx.expectedType, actual).withEmptyContext())
      }
      Unit
    }

  override fun visitApplication(
    application: Application,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitProgram(
    program: Program,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunctionDeclaration(
    functionDeclaration: FunctionDeclaration,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitParamDeclaration(
    paramDeclaration: ParamDeclaration,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunParamDeclaration(
    funDeclaration: FunDeclaration,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeBool(
    typeBool: ast.Type.Bool,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeNat(
    typeNat: ast.Type.Nat,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeFun(
    typeFun: ast.Type.Fun,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTrueLiteral(
    trueLiteral: TrueLiteral,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> = binding {
    if (ctx.expectedType != Bool) {
      raise(TypeMismatch(trueLiteral, ctx.expectedType, ctx.expectedType).withEmptyContext())
    }
    Unit
  }

  override fun visitFalseLiteral(
    falseLiteral: FalseLiteral,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> = binding {
    if (ctx.expectedType != Bool) {
      raise(TypeMismatch(falseLiteral, ctx.expectedType, ctx.expectedType).withEmptyContext())
    }
    Unit
  }

  override fun visitIfExpr(
    expression: IfExpression,
    ctx: TypeCheckCtx,
  ): Result<Unit, ContextualTypeError> = binding {
    expression.cond.accept(this@TypeCheckVisitor, ctx.copy(expectedType = Bool)).bind()
    expression.thenBranch.accept(this@TypeCheckVisitor, ctx).bind()
    expression.elseBranch.accept(this@TypeCheckVisitor, ctx).bind()
  }
}
