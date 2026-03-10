package type

import ast.Application
import ast.Declaration
import ast.Expr
import ast.FalseLiteral
import ast.FunDeclaration
import ast.FunctionDeclaration
import ast.IfExpression
import ast.IsZero
import ast.NatRec
import ast.Node
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
import type.error.TypeMismatch
import type.error.UndefinedVariable
import type.error.withContextLayer
import type.error.withEmptyContext
import utils.raise

fun checkType(expr: Expr, env: Env, expected: Type): Result<Unit, ContextualTypeError> =
  when (expr) {
    is Succ ->
      binding {
        if (expected != Nat) {
          raise(TypeMismatch(expr, expected, Nat).withEmptyContext())
        }
        checkType(expr.expr, env, expected).bind()
      }

    is Var ->
      binding {
        val actual = env[expr.name] ?: raise(UndefinedVariable(expr).withEmptyContext())
        if (actual != expected) {
          raise(TypeMismatch(expr, expected, actual).withEmptyContext())
        }
        type.Unit
      }

    is TrueLiteral ->
      binding {
        if (expected != Bool) {
          raise(TypeMismatch(expr, expected, expected).withEmptyContext())
        }
        type.Unit
      }

    is FalseLiteral ->
      binding {
        if (expected != Bool) {
          raise(TypeMismatch(expr, Bool, expected).withEmptyContext())
        }
        type.Unit
      }

    is IfExpression ->
      binding {
        checkType(expr.cond, env, Bool).bind()
        checkType(expr.thenBranch, env, expected).bind()
        checkType(expr.elseBranch, env, expected).bind()
      }

    is IsZero ->
      binding {
        if (expected != Nat) {
          raise(TypeMismatch(expr, Nat, expected).withEmptyContext())
        }
        type.Unit
      }

    else -> error("Not implemented")
  }

fun inferExprType(expr: Expr, env: Env): Result<Type, ContextualTypeError> =
  when (expr) {
    is Succ ->
      binding {
        checkType(expr.expr, env, Nat).wrapError(expr).bind()
        Nat
      }

    is Var -> binding { env[expr.name] ?: raise(UndefinedVariable(expr).withEmptyContext()) }

    is Application ->
      binding {
        val funcType = inferExprType(expr.func, env).wrapError(expr).bind()
        if (funcType !is FunType) {
          raise(ApplicantNotOfFunctionType(expr).withEmptyContext())
        }
        checkType(expr.args.single(), env, funcType.inputTypes.single()).wrapError(expr).bind()
        funcType.retType
      }

    is TrueLiteral -> Ok(Bool)
    is FalseLiteral -> Ok(Bool)

    is IfExpression ->
      binding {
        checkType(expr.cond, env, Bool).bind()
        val inferredType = inferExprType(expr.thenBranch, env).bind()
        checkType(expr.cond, env, inferredType).bind()
        inferredType
      }

    is IsZero ->
      binding {
        checkType(expr.arg, env, Nat).bind()
        Bool
      }
    is NatRec -> binding { error("Not implemented yet") }
  }

fun inferDeclType(decl: Declaration, env: Env): Result<Type, ContextualTypeError> =
  when (decl) {
    is FunctionDeclaration ->
      binding {
        val paramEnv = decl.parameterDeclarations.associate { it.name to it.type.toType() }
        val updatedEnv = env + paramEnv
        val inferredRetType = inferExprType(decl.returnExpr, updatedEnv).wrapError(decl).bind()
        val inputParams = decl.parameterDeclarations.map { it.type.toType() }
        FunType(inputParams, inferredRetType)
      }

    is FunDeclaration -> error("inferDeclType not yet implemented for FunDeclaration")
  }

fun inferProgramType(program: Program, env: Env): Result<Type, ContextualTypeError> = binding {
  var currentEnv = env
  for (declaration in program.declarations) {
    val inferredType = inferDeclType(declaration, currentEnv).wrapError(program).bind()
    if (declaration is FunctionDeclaration) {
      currentEnv = currentEnv + (declaration.name to inferredType)
    }
  }
  type.Unit
}

fun performTypeInference(node: Node): Result<Type, ContextualTypeError> =
  when (node) {
    is Program -> inferProgramType(node, defaultEnv)
    is Expr -> inferExprType(node, defaultEnv)
    is Declaration -> inferDeclType(node, defaultEnv)
    else -> error("Unsupported type inference for ${node::class.simpleName}")
  }

private fun <V> Result<V, ContextualTypeError>.wrapError(currentNode: Node) = mapError {
  it.withContextLayer(TypeErrorFrame.WhileInferring(currentNode))
}
