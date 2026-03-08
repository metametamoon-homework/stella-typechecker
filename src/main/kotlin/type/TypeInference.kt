package type

import ast.Application
import ast.AstVisitor
import ast.FalseLiteral
import ast.FunDeclaration
import ast.FunctionDeclaration
import ast.IfExpression
import ast.Node
import ast.ParamDeclaration
import ast.Program
import ast.Succ
import ast.TrueLiteral
import ast.Var
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import type.error.ApplicantNotOfFunctionType
import type.error.ContextualTypeError
import type.error.TypeErrorFrame
import type.error.UndefinedVariable
import type.error.withContextLayer
import type.error.withEmptyContext
import utils.raise

data class TypeInferenceCtx(val env: Env, val typecheckVisitor: TypeCheckVisitor)

@Suppress("TooManyFunctions")
class TypeInferenceVisitor : AstVisitor<TypeInferenceCtx, Result<Type, ContextualTypeError>> {
  @Suppress("MemberNameEqualsClassName") private val typeInferenceVisitor = this

  override fun visitSucc(succ: Succ, ctx: TypeInferenceCtx): Result<Type, ContextualTypeError> =
    binding {
      succ.expr
        .accept(ctx.typecheckVisitor, TypeCheckCtx(ctx.env, Nat, typeInferenceVisitor))
        .wrapError(succ)
        .bind()
      Nat
    }

  override fun visitVar(varExpr: Var, ctx: TypeInferenceCtx): Result<Type, ContextualTypeError> =
    binding {
      ctx.env[varExpr.name] ?: raise(UndefinedVariable(varExpr).withEmptyContext())
    }

  override fun visitApplication(
    application: Application,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> = binding {
    val leftType = application.func.accept(typeInferenceVisitor, ctx).wrapError(application).bind()
    if (leftType !is FunType) {
      raise(ApplicantNotOfFunctionType(application).withEmptyContext())
    }
    application.args
      .single()
      .accept(
        ctx.typecheckVisitor,
        TypeCheckCtx(ctx.env, leftType.inputTypes.single(), typeInferenceVisitor),
      )
      .wrapError(application)
      .bind()
    leftType.retType
  }

  override fun visitProgram(
    program: Program,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> = binding {
    val deltaEnv = mutableMapOf<String, type.Type>()
    for (declaration in program.declarations) {
      val fullEnv = (ctx.env.toList() + deltaEnv.toList()).toMap()
      val inferredType =
        declaration.accept(typeInferenceVisitor, ctx.copy(env = fullEnv)).wrapError(program).bind()
      if (declaration is FunctionDeclaration) {
        deltaEnv[declaration.name] = inferredType
      }
    }
    type.Unit
  }

  override fun visitFunctionDeclaration(
    functionDeclaration: FunctionDeclaration,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> = binding {
    val deltaEnv =
      functionDeclaration.parameterDeclarations.map { paramDecl ->
        paramDecl.name to paramDecl.type.toType()
      }
    val updatedEnv = (ctx.env.toList() + deltaEnv).toMap()
    val inferredRetType =
      functionDeclaration.returnExpr
        .accept(typeInferenceVisitor, TypeInferenceCtx(updatedEnv, ctx.typecheckVisitor))
        .wrapError(functionDeclaration)
        .bind()
    val inputParams = functionDeclaration.parameterDeclarations.map { it.type.toType() }
    FunType(inputParams, inferredRetType)
  }

  override fun visitParamDeclaration(
    paramDeclaration: ParamDeclaration,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitFunParamDeclaration(
    funDeclaration: FunDeclaration,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeBool(
    typeBool: ast.Type.Bool,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeNat(
    typeNat: ast.Type.Nat,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTypeFun(
    typeFun: ast.Type.Fun,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> {
    TODO("Not yet implemented")
  }

  override fun visitTrueLiteral(
    trueLiteral: TrueLiteral,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> = Ok(Bool)

  override fun visitFalseLiteral(
    falseLiteral: FalseLiteral,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> = Ok(Bool)

  override fun visitIfExpr(
    expression: IfExpression,
    ctx: TypeInferenceCtx,
  ): Result<Type, ContextualTypeError> = binding {
    expression.cond.typeCheck(ctx.env, Bool, ctx.typecheckVisitor).bind()
    val inferredType = expression.thenBranch.inferType(ctx.env, ctx.typecheckVisitor).bind()
    expression.cond.typeCheck(ctx.env, inferredType, ctx.typecheckVisitor).bind()
    inferredType
  }

  private fun Node.typeCheck(
    env: Env,
    expectedType: Type,
    typeChecker: TypeCheckVisitor,
  ): Result<Unit, ContextualTypeError> =
    accept(typeChecker, TypeCheckCtx(env, expectedType, typeInferenceVisitor))

  private fun Node.inferType(
    env: Env,
    typeChecker: TypeCheckVisitor,
  ): Result<Type, ContextualTypeError> =
    accept(typeInferenceVisitor, TypeInferenceCtx(env, typeChecker))
}

private fun <V> Result<V, ContextualTypeError>.wrapError(currentNode: Node) = mapError {
  it.withContextLayer(TypeErrorFrame.WhileInferring(currentNode))
}

fun performTypeInference(node: Node): Result<Type, ContextualTypeError> =
  node.accept(TypeInferenceVisitor(), TypeInferenceCtx(defaultEnv, TypeCheckVisitor()))
