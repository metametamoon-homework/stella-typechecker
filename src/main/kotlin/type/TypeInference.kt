package type

import ast.Application
import ast.AstVisitor
import ast.FunDeclaration
import ast.FunctionDeclaration
import ast.ParamDeclaration
import ast.Program
import ast.Succ
import ast.Var
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import utils.raise

data class TypeInferenceCtx(val env: Env, val typecheckVisitor: TypeCheckVisitor)

class TypeInferenceVisitor : AstVisitor<TypeInferenceCtx, Result<Type, TypeError>> {
  @Suppress("MemberNameEqualsClassName") private val typeInferenceVisitor = this

  override fun visitSucc(succ: Succ, ctx: TypeInferenceCtx): Result<Type, TypeError> = binding {
    val checkUnderlying =
      succ.expr
        .accept(ctx.typecheckVisitor, TypeCheckCtx(ctx.env, Nat, typeInferenceVisitor))
        .bind()
    if (checkUnderlying) {
      Nat
    } else {
      raise(SuccInferenceError(succ))
    }
  }

  override fun visitVar(varExpr: Var, ctx: TypeInferenceCtx): Result<Type, TypeError> = binding {
    ctx.env[varExpr.name] ?: raise(VarInferenceError(varExpr))
  }

  override fun visitApplication(
    application: Application,
    ctx: TypeInferenceCtx,
  ): Result<Type, TypeError> {
    @Suppress("UnusedVariable") val leftType = application.func.accept(typeInferenceVisitor, ctx)
    TODO("Not yet implemented")
  }

  override fun visitProgram(program: Program, ctx: TypeInferenceCtx): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunctionDeclaration(
    functionDeclaration: FunctionDeclaration,
    ctx: TypeInferenceCtx,
  ): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitParamDeclaration(
    paramDeclaration: ParamDeclaration,
    ctx: TypeInferenceCtx,
  ): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunParamDeclaration(
    funDeclaration: FunDeclaration,
    ctx: TypeInferenceCtx,
  ): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeBool(
    typeBool: ast.Type.Bool,
    ctx: TypeInferenceCtx,
  ): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeNat(typeNat: ast.Type.Nat, ctx: TypeInferenceCtx): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeFun(typeFun: ast.Type.Fun, ctx: TypeInferenceCtx): Result<Type, TypeError> {
    TODO("Not yet implemented")
  }
}
