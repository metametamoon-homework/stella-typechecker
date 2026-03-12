package type

import ast.Abstraction
import ast.Application
import ast.Declaration
import ast.Expr
import ast.FalseLiteral
import ast.FunDeclaration
import ast.FunctionDeclaration
import ast.IfExpression
import ast.IntLiteral
import ast.IsZero
import ast.NatRec
import ast.Node
import ast.Program
import ast.Succ
import ast.TrueLiteral
import ast.TupleDotExpression
import ast.TupleLiteral
import ast.UnitConstant
import ast.Var
import com.github.michaelbull.result.BindingScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import type.error.ApplicantNotOfFunctionType
import type.error.ContextualTypeError
import type.error.NotAFunction
import type.error.NotATuple
import type.error.TupleIndexOutOfBound
import type.error.TypeError
import type.error.TypeErrorFrame
import type.error.TypeMismatch
import type.error.UndefinedVariable
import type.error.withContextLayer
import type.error.withEmptyContext
import utils.raise

// a gigantic when is going to be complex, but there is no work around it
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun checkType(expr: Expr, env: Env, expected: Type): Result<Unit, ContextualTypeError> {
  val result: Result<Unit, ContextualTypeError> =
    when (expr) {
      is Succ ->
        binding {
          assertExpectedTypeOrReport(Nat, expected, expr)
          checkType(expr.expr, env, expected).bind()
        }

      is Var ->
        binding {
          val actual = env[expr.name] ?: raise(UndefinedVariable(expr).withEmptyContext())
          assertExpectedTypeOrReport(actual, expected, expr)
          Unit
        }

      is TrueLiteral ->
        binding {
          assertExpectedTypeOrReport(Bool, expected, expr)
          Unit
        }

      is FalseLiteral ->
        binding {
          assertExpectedTypeOrReport(Bool, expected, expr)
          Unit
        }

      is IfExpression ->
        binding {
          checkType(expr.cond, env, Bool).bind()
          checkType(expr.thenBranch, env, expected).bind()
          checkType(expr.elseBranch, env, expected).bind()
        }

      is IsZero ->
        binding {
          assertExpectedTypeOrReport(Bool, expected, expr)
          checkType(expr.arg, env, Nat).bind()
          Unit
        }

      is Abstraction ->
        binding {
          val abstractionType = inferExprType(expr, env).bind()
          assertExpectedTypeOrReport(abstractionType, expected, expr)
          Unit
        }

      is UnitConstant ->
        binding {
          assertExpectedTypeOrReport(Unit, expected, expr)
          Unit
        }

      is Application ->
        binding {
          val leftType = inferExprType(expr.func, env).bind()
          if (leftType !is FunType) {
            raise(NotAFunction(expr.func).withEmptyContext())
          }
          checkType(expr.args.single(), env, leftType.inputTypes.single()).bind()
          if (leftType.retType != expected) {
            raise(TypeMismatch(expr, expected, leftType.retType).withEmptyContext())
          }
          Unit
        }
      is IntLiteral ->
        binding {
          assertExpectedTypeOrReport(Nat, expected, expr)
          Unit
        }
      is NatRec ->
        binding {
          checkType(expr.n, env, Nat).bind()
          val initType = inferType(expr.init, env).bind()
          val expectedStepType = FunType(listOf(Nat), FunType(listOf(initType), initType))
          checkType(expr.step, env, expectedStepType).bind()
          Unit
        }

      is TupleLiteral ->
        binding {
          val projectionTypes = expr.projections.map { inferType(it, env).bind() }
          val actualType = TupleType(projectionTypes)
          assertExpectedTypeOrReport(actualType, expected, expr)
          Unit
        }
      is TupleDotExpression ->
        binding {
          val actualType = inferExprType(expr, env).bind()
          assertExpectedTypeOrReport(actualType, expected, expr)
          Unit
        }
    }
  return result.wrapWhileTypechecking(expr, expected)
}

private fun BindingScope<ContextualTypeError>.raise(e: TypeError): Nothing =
  Err(e.withEmptyContext()).bind()

private fun BindingScope<ContextualTypeError>.assertExpectedTypeOrReport(
  actualType: type.Type,
  expected: type.Type,
  expr: Expr,
) {
  if (actualType != expected) {
    raise(TypeMismatch(expr, expected, actualType).withEmptyContext())
  }
}

// a gigantic when is going to be complex, but there is no work around it
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun inferExprType(expr: Expr, env: Env): Result<Type, ContextualTypeError> {
  val result: Result<Type, ContextualTypeError> =
    when (expr) {
      is Succ ->
        binding {
          checkType(expr.expr, env, Nat).bind()
          Nat
        }

      is Var -> binding { env[expr.name] ?: raise(UndefinedVariable(expr).withEmptyContext()) }

      is Application ->
        binding {
          val funcType = inferExprType(expr.func, env).bind()
          if (funcType !is FunType) {
            raise(ApplicantNotOfFunctionType(expr).withEmptyContext())
          }
          checkType(expr.args.single(), env, funcType.inputTypes.single()).bind()

          funcType.retType
        }

      is TrueLiteral -> Ok(Bool)
      is FalseLiteral -> Ok(Bool)

      is IfExpression ->
        binding {
          checkType(expr.cond, env, Bool).bind()
          val inferredType = inferExprType(expr.thenBranch, env).bind()
          checkType(expr.elseBranch, env, inferredType).bind()
          inferredType
        }

      is IsZero ->
        binding {
          checkType(expr.arg, env, Nat).bind()
          Bool
        }

      is NatRec ->
        binding {
          checkType(expr.n, env, Nat).bind()
          val exprType = inferExprType(expr.init, env).bind()
          checkType(expr.step, env, FunType(listOf(Nat), FunType(listOf(exprType), exprType)))
            .bind()
          exprType
        }

      is IntLiteral -> binding { Nat }
      is Abstraction ->
        binding {
          val paramEnv = expr.params.associate { it.name to it.type.toType() }
          val updatedEnv = env + paramEnv
          val returnType = inferExprType(expr.returnExpr, updatedEnv).bind()
          FunType(expr.params.map { it.type.toType() }, returnType)
        }

      is UnitConstant -> binding { Unit }
      is TupleLiteral ->
        binding {
          val projectionTypes = expr.projections.map { inferType(it, env).bind() }
          TupleType(projectionTypes)
        }

      is TupleDotExpression ->
        binding {
          val receiverType = inferType(expr.tupleExpr, env).bind()
          if (receiverType !is TupleType) {
            raise(NotATuple(expr))
          }
          if (receiverType.projections.size < expr.index) {
            raise(TupleIndexOutOfBound(expr))
          }
          receiverType.projections[expr.index - 1]
        }
    }
  return result.wrapWhileInferring(expr)
}

fun inferDeclType(decl: Declaration, env: Env): Result<Type, ContextualTypeError> =
  when (decl) {
    is FunctionDeclaration ->
      binding {
        val paramEnv = decl.parameterDeclarations.associate { it.name to it.type.toType() }
        val updatedEnv = env + paramEnv
        val specifiedReturnType = decl.returnType?.toType()
        val returnType =
          if (specifiedReturnType != null) {
            checkType(decl.returnExpr, updatedEnv, specifiedReturnType).bind()
            specifiedReturnType
          } else {
            inferExprType(decl.returnExpr, updatedEnv).wrapWhileInferring(decl).bind()
          }
        val inputParams = decl.parameterDeclarations.map { it.type.toType() }
        FunType(inputParams, returnType)
      }

    is FunDeclaration -> error("inferDeclType not yet implemented for FunDeclaration")
  }

fun inferProgramType(program: Program, env: Env): Result<Type, ContextualTypeError> = binding {
  var currentEnv = env
  for (declaration in program.declarations) {
    val inferredType = inferDeclType(declaration, currentEnv).wrapWhileInferring(program).bind()
    if (declaration is FunctionDeclaration) {
      currentEnv = currentEnv + (declaration.name to inferredType)
    }
  }
  type.Unit
}

fun inferType(node: Node, env: Env): Result<Type, ContextualTypeError> =
  when (node) {
    is Program -> inferProgramType(node, env)
    is Expr -> inferExprType(node, env)
    is Declaration -> inferDeclType(node, env)
    else -> error("Unsupported type inference for ${node::class.simpleName}")
  }

private fun <V> Result<V, ContextualTypeError>.wrapWhileInferring(currentNode: Node) = mapError {
  it.withContextLayer(TypeErrorFrame.WhileInferring(currentNode))
}

private fun <V> Result<V, ContextualTypeError>.wrapWhileTypechecking(
  currentNode: Node,
  expected: Type,
) = mapError { it.withContextLayer(TypeErrorFrame.WhileTypeChecking(currentNode, expected)) }
