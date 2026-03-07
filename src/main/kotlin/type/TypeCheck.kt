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

data class TypeCheckCtx(
  val env: Env,
  val expectedType: Type,
  val typeInferenceVisitor: TypeInferenceVisitor,
)

class TypeCheckVisitor : AstVisitor<TypeCheckCtx, Result<Boolean, TypeError>> {
  override fun visitSucc(
    succ: Succ,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitVar(
    varExpr: Var,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitApplication(
    application: Application,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitProgram(
    program: Program,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunctionDeclaration(
    functionDeclaration: FunctionDeclaration,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitParamDeclaration(
    paramDeclaration: ParamDeclaration,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunParamDeclaration(
    funDeclaration: FunDeclaration,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeBool(
    typeBool: ast.Type.Bool,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeNat(
    typeNat: ast.Type.Nat,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeFun(
    typeFun: ast.Type.Fun,
    ctx: TypeCheckCtx
  ): Result<Boolean, TypeError> {
    TODO("Not yet implemented")
  }
}
