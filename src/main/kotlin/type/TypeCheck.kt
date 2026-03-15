package type

import ast.Abstraction
import ast.Application
import ast.Binding
import ast.ConsList
import ast.Declaration
import ast.Expr
import ast.FalseLiteral
import ast.Fix
import ast.FunDeclaration
import ast.FunctionDeclaration
import ast.IfExpression
import ast.Inl
import ast.Inr
import ast.IntLiteral
import ast.IsZero
import ast.LetBinding
import ast.ListHead
import ast.ListIsEmpty
import ast.ListLiteral
import ast.ListTail
import ast.Match
import ast.NatRec
import ast.Node
import ast.Pattern
import ast.Pred
import ast.Program
import ast.RecordDotExpression
import ast.RecordLiteral
import ast.Succ
import ast.TrueLiteral
import ast.TupleDotExpression
import ast.TupleLiteral
import ast.TypeAscription
import ast.UnitConstant
import ast.Var
import ast.VariantLiteral
import com.github.michaelbull.result.BindingScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import type.error.AmbiguousList
import type.error.AmbiguousSumType
import type.error.AmbiguousVariantType
import type.error.ApplicantNotOfFunctionType
import type.error.ContextualTypeError
import type.error.DuplicateRecordFields
import type.error.DuplicateRecordTypeFields
import type.error.MissingMain
import type.error.MissingRecordFields
import type.error.NonExhaustivePatternMatching
import type.error.NotAFunction
import type.error.NotAList
import type.error.NotARecord
import type.error.NotASumType
import type.error.NotATuple
import type.error.NotAVariantType
import type.error.TupleIndexOutOfBound
import type.error.TypeError
import type.error.TypeErrorFrame
import type.error.TypeMismatch
import type.error.UndefinedVariable
import type.error.UnexpectedFieldAccess
import type.error.UnexpectedInjection
import type.error.UnexpectedLambda
import type.error.UnexpectedLambdaParameterType
import type.error.UnexpectedList
import type.error.UnexpectedRecord
import type.error.UnexpectedRecordField
import type.error.UnexpectedRecordFields
import type.error.UnexpectedTuple
import type.error.UnexpectedTupleLength
import type.error.UnexpectedVariant
import type.error.UnexpectedVariantLabel
import type.error.withContextLayer
import type.error.withEmptyContext

fun matchPatternWithType(pattern: Pattern, type: Type): Result<Env, ContextualTypeError> =
  when (pattern) {
    is Pattern.Variable -> Ok(mapOf(pattern.name to type))
    is Pattern.Inl ->
      binding {
        if (type !is SumType) raise(NotASumType(pattern))
        matchPatternWithType(pattern.inner, type.left).bind()
      }
    is Pattern.Inr ->
      binding {
        if (type !is SumType) raise(NotASumType(pattern))
        matchPatternWithType(pattern.inner, type.right).bind()
      }
    is Pattern.Variant ->
      binding {
        if (type !is VariantType) raise(NotAVariantType(pattern))
        if (pattern.label !in type.fields) raise(UnexpectedVariantLabel(pattern, pattern.label))
        val fieldType = type.fields[pattern.label]
        if (pattern.inner != null && fieldType != null) {
          matchPatternWithType(pattern.inner, fieldType).bind()
        } else {
          emptyMap()
        }
      }
  }

private fun BindingScope<ContextualTypeError>.resolveBindings(
  bindings: List<Binding>,
  env: Env,
): Env {
  var currentEnv = env
  for (binding in bindings) {
    val rhsType = inferExprType(binding.expr, currentEnv).bind()
    val envUpdate = matchPatternWithType(binding.pattern, rhsType).bind()
    currentEnv = currentEnv + envUpdate
  }
  return currentEnv
}

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
          val actual = env[expr.name] ?: raise(UndefinedVariable(expr))
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

      is Pred ->
        binding {
          assertExpectedTypeOrReport(Nat, expected, expr)
          checkType(expr.arg, env, Nat).bind()
          Unit
        }

      is Abstraction ->
        binding {
          expr.params.forEach { checkRecordTypeDuplicates(it.type) }
          if (expected !is FunType) {
            raise(UnexpectedLambda(expr, expected))
          }
          val inputType = expected.inputTypes.single()
          if (inputType != expr.params.single().type.toType()) {
            raise(UnexpectedLambdaParameterType(expr.params.single(), inputType))
          }
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
            raise(NotAFunction(expr.func))
          }
          checkType(expr.args.single(), env, leftType.inputTypes.single()).bind()
          if (leftType.retType != expected) {
            raise(TypeMismatch(expr, expected, leftType.retType))
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
          if (expected !is TupleType) raise(UnexpectedTuple(expr, expected))
          if (expr.projections.size != expected.projections.size) {
            raise(UnexpectedTupleLength(expr, expected.projections.size, expr.projections.size))
          }
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
      is RecordLiteral ->
        binding {
          if (expected !is RecordType) raise(UnexpectedRecord(expr, expected))
          val duplicates = findDuplicateKeys(expr.bindings)
          if (duplicates.isNotEmpty()) raise(DuplicateRecordFields(expr, duplicates))
          val bindingsMap = expr.bindings.toMap()
          val expectedKeys = expected.fields.keys
          val actualKeys = bindingsMap.keys
          val missing = expectedKeys - actualKeys
          val extra = actualKeys - expectedKeys
          if (missing.isNotEmpty()) raise(MissingRecordFields(expr, missing))
          if (extra.isNotEmpty()) raise(UnexpectedRecordFields(expr, extra))
          for ((field, expectedFieldType) in expected.fields) {
            checkType(bindingsMap.getValue(field), env, expectedFieldType).bind()
          }
          Unit
        }
      is RecordDotExpression ->
        binding {
          val receiverType = inferType(expr.recordExpr, env).bind()
          if (receiverType !is RecordType) raise(NotARecord(expr))
          if (expr.label !in receiverType.fields)
            raise(UnexpectedFieldAccess(expr, receiverType, expr.label))
          val fieldType = receiverType.fields.getValue(expr.label)
          assertExpectedTypeOrReport(fieldType, expected, expr)
          Unit
        }

      is TypeAscription ->
        binding {
          val ascribed = expr.type.toType()
          checkType(expr.expr, env, ascribed).bind()
          assertExpectedTypeOrReport(ascribed, expected, expr)
          Unit
        }

      is LetBinding ->
        binding {
          val updatedEnv = resolveBindings(expr.bindings, env)
          checkType(expr.body, updatedEnv, expected).bind()
        }
      is Inl ->
        binding {
          if (expected !is SumType) raise(UnexpectedInjection(expr, expected))
          checkType(expr.expr, env, expected.left).bind()
        }
      is Inr ->
        binding {
          if (expected !is SumType) raise(UnexpectedInjection(expr, expected))
          checkType(expr.expr, env, expected.right).bind()
        }
      is ListLiteral ->
        binding {
          if (expected !is ListType) raise(UnexpectedList(expr, expected))
          for (element in expr.elements) {
            checkType(element, env, expected.elementType).bind()
          }
          Unit
        }
      is ConsList ->
        binding {
          if (expected !is ListType) raise(UnexpectedList(expr, expected))
          checkType(expr.head, env, expected.elementType).bind()
          checkType(expr.tail, env, expected).bind()
        }
      is ListHead ->
        binding {
          val actualType = inferExprType(expr, env).bind()
          assertExpectedTypeOrReport(actualType, expected, expr)
          Unit
        }
      is ListTail ->
        binding {
          val actualType = inferExprType(expr, env).bind()
          assertExpectedTypeOrReport(actualType, expected, expr)
          Unit
        }
      is ListIsEmpty ->
        binding {
          val actualType = inferExprType(expr, env).bind()
          assertExpectedTypeOrReport(actualType, expected, expr)
          Unit
        }
      is Match ->
        binding {
          val scrutineeType = inferExprType(expr.scrutinee, env).bind()
          checkExhaustiveness(expr, scrutineeType)
          for (case in expr.cases) {
            val patEnv = matchPatternWithType(case.pattern, scrutineeType).bind()
            checkType(case.expr, env + patEnv, expected).bind()
          }
          Unit
        }
      is VariantLiteral ->
        binding {
          if (expected !is VariantType) raise(UnexpectedVariant(expr, expected))
          if (expr.label !in expected.fields) raise(UnexpectedVariantLabel(expr, expr.label))
          val fieldType = expected.fields.getValue(expr.label)
          checkType(expr.expr, env, fieldType).bind()
          Unit
        }
      is Fix -> binding { checkType(expr.expr, env, FunType(listOf(expected), expected)).bind() }
      is Pattern.Variable -> error("unreachable")
      is Pattern.Inl -> error("unreachable")
      is Pattern.Inr -> error("unreachable")
      is Pattern.Variant -> error("unreachable")
    }
  return result.wrapWhileTypechecking(expr, expected)
}

private fun BindingScope<ContextualTypeError>.checkExhaustiveness(match: Match, type: Type) {
  val hasWildcard = match.cases.any { it.pattern is Pattern.Variable }
  if (hasWildcard) return
  if (type is SumType) {
    val isExhaustive =
      match.cases.any { it.pattern is Pattern.Inr } && match.cases.any { it.pattern is Pattern.Inl }
    if (!isExhaustive) {
      raise(NonExhaustivePatternMatching(match))
    }
  }
  if (type is VariantType) {
    val coveredLabels = match.cases.mapNotNull { (it.pattern as? Pattern.Variant)?.label }.toSet()
    if (!coveredLabels.containsAll(type.fields.keys)) {
      raise(NonExhaustivePatternMatching(match))
    }
  }
}

private fun BindingScope<ContextualTypeError>.raise(e: TypeError): Nothing =
  Err(e.withEmptyContext()).bind()

private fun BindingScope<ContextualTypeError>.assertExpectedTypeOrReport(
  actualType: type.Type,
  expected: type.Type,
  expr: Expr,
) {
  if (actualType != expected) {
    raise(TypeMismatch(expr, expected, actualType))
  }
}

private fun <V> findDuplicateKeys(pairs: List<Pair<String, V>>): Set<String> {
  val seen = mutableSetOf<String>()
  val duplicates = mutableSetOf<String>()
  for ((key, _) in pairs) {
    if (!seen.add(key)) duplicates.add(key)
  }
  return duplicates
}

private fun BindingScope<ContextualTypeError>.checkRecordTypeDuplicates(type: ast.Type) {
  when (type) {
    is ast.Type.Record -> {
      val duplicates = findDuplicateKeys(type.projections)
      if (duplicates.isNotEmpty()) raise(DuplicateRecordTypeFields(type, duplicates))
      type.projections.forEach { (_, t) -> checkRecordTypeDuplicates(t) }
    }
    is ast.Type.Fun -> {
      type.inputTypes.forEach { checkRecordTypeDuplicates(it) }
      checkRecordTypeDuplicates(type.returnType)
    }
    is ast.Type.Tuple -> type.projections.forEach { checkRecordTypeDuplicates(it) }
    is ast.Type.Sum -> {
      checkRecordTypeDuplicates(type.left)
      checkRecordTypeDuplicates(type.right)
    }
    is ast.Type.ListType -> checkRecordTypeDuplicates(type.elementType)
    is ast.Type.Variant -> type.fields.forEach { checkRecordTypeDuplicates(it.type) }
    ast.Type.Bool,
    ast.Type.Nat,
    ast.Type.Unit -> {}
  }
}

// a gigantic when is going to be complex
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun inferExprType(expr: Expr, env: Env): Result<Type, ContextualTypeError> {
  val result: Result<Type, ContextualTypeError> =
    when (expr) {
      is Succ ->
        binding {
          checkType(expr.expr, env, Nat).bind()
          Nat
        }

      is Var -> binding { env[expr.name] ?: raise(UndefinedVariable(expr)) }

      is Application ->
        binding {
          val funcType = inferExprType(expr.func, env).bind()
          if (funcType !is FunType) {
            raise(ApplicantNotOfFunctionType(expr))
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

      is Pred ->
        binding {
          checkType(expr.arg, env, Nat).bind()
          Nat
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
          expr.params.forEach { checkRecordTypeDuplicates(it.type) }
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
      is RecordLiteral ->
        binding {
          val duplicates = findDuplicateKeys(expr.bindings)
          if (duplicates.isNotEmpty()) raise(DuplicateRecordFields(expr, duplicates))
          val fieldTypes = expr.bindings.toMap().mapValues { (_, v) -> inferType(v, env).bind() }
          RecordType(fieldTypes)
        }
      is RecordDotExpression ->
        binding {
          val receiverType = inferType(expr.recordExpr, env).bind()
          if (receiverType !is RecordType) {
            raise(NotARecord(expr))
          }
          receiverType.fields[expr.label] ?: raise(UnexpectedRecordField(expr, expr.label))
        }
      is TypeAscription ->
        binding {
          val ascribed = expr.type.toType()
          checkType(expr.expr, env, ascribed).bind()
          ascribed
        }

      is LetBinding ->
        binding {
          val updatedEnv = resolveBindings(expr.bindings, env)
          inferExprType(expr.body, updatedEnv).bind()
        }
      is Pattern.Variable -> error("unreachable")
      is ListLiteral ->
        binding {
          if (expr.elements.isEmpty()) raise(AmbiguousList(expr))
          val firstType = inferExprType(expr.elements.first(), env).bind()
          for (element in expr.elements.drop(1)) {
            checkType(element, env, firstType).bind()
          }
          ListType(firstType)
        }
      is ConsList ->
        binding {
          val headType = inferExprType(expr.head, env).bind()
          val tailExpected = ListType(headType)
          checkType(expr.tail, env, tailExpected).bind()
          tailExpected
        }
      is ListHead ->
        binding {
          val listType = inferExprType(expr.list, env).bind()
          if (listType !is ListType) raise(NotAList(expr.list))
          listType.elementType
        }
      is ListTail ->
        binding {
          val listType = inferExprType(expr.list, env).bind()
          if (listType !is ListType) raise(NotAList(expr.list))
          listType
        }
      is ListIsEmpty ->
        binding {
          val listType = inferExprType(expr.list, env).bind()
          if (listType !is ListType) raise(NotAList(expr.list))
          Bool
        }
      is Inl -> Err(AmbiguousSumType(expr).withEmptyContext())
      is Inr -> Err(AmbiguousSumType(expr).withEmptyContext())
      is Match ->
        binding {
          val scrutineeType = inferExprType(expr.scrutinee, env).bind()
          checkExhaustiveness(expr, scrutineeType)
          val firstCase = expr.cases.firstOrNull() ?: error("empty match")
          val firstPatEnv = matchPatternWithType(firstCase.pattern, scrutineeType).bind()
          val resultType = inferExprType(firstCase.expr, env + firstPatEnv).bind()
          for (case in expr.cases.drop(1)) {
            val patEnv = matchPatternWithType(case.pattern, scrutineeType).bind()
            checkType(case.expr, env + patEnv, resultType).bind()
          }
          resultType
        }
      is VariantLiteral -> Err(AmbiguousVariantType(expr).withEmptyContext())
      is Fix ->
        binding {
          val fixArg = expr.expr
          val innerType = inferExprType(fixArg, env).bind()
          if (innerType !is FunType) raise(NotAFunction(fixArg))
          if (innerType.inputTypes.size != 1) raise(NotAFunction(fixArg)) // close enough
          val inputType = innerType.inputTypes.single()
          if (inputType != innerType.retType) {
            raise(TypeMismatch(fixArg, FunType(listOf(inputType), inputType), innerType))
          }
          innerType.retType
        }
      is Pattern.Inl -> error("unreachable")
      is Pattern.Inr -> error("unreachable")
      is Pattern.Variant -> error("unreachable")
    }
  return result.wrapWhileInferring(expr)
}

fun inferDeclType(decl: Declaration, env: Env): Result<Type, ContextualTypeError> =
  when (decl) {
    is FunctionDeclaration ->
      binding {
        // we only check for record types problems within types specified in a function declaration
        // technically, there might be types specified elsewhere, but we currently do not consider
        // it
        decl.parameterDeclarations.forEach { checkRecordTypeDuplicates(it.type) }
        if (decl.returnType != null) checkRecordTypeDuplicates(decl.returnType)
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
  val hasMain = program.declarations.any { it is FunctionDeclaration && it.name == "main" }
  if (!hasMain) {
    raise(MissingMain(program))
  }
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
