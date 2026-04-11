package type

import ast.Abstraction
import ast.Application
import ast.Assign
import ast.Binding
import ast.ConsList
import ast.Declaration
import ast.Deref
import ast.ExceptionTypeDeclaration
import ast.Expr
import ast.FalseLiteral
import ast.Fix
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
import ast.NewRef
import ast.Node
import ast.Panic
import ast.Pattern
import ast.Pred
import ast.Program
import ast.RecordDotExpression
import ast.RecordLiteral
import ast.Sequence
import ast.Succ
import ast.Throw
import ast.TrueLiteral
import ast.TryCatch
import ast.TryWith
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
import kotlin.error
import type.error.AmbiguousList
import type.error.AmbiguousPanic
import type.error.AmbiguousSumType
import type.error.AmbiguousThrow
import type.error.AmbiguousVariantType
import type.error.ContextualTypeError
import type.error.DuplicateFunctionDeclaration
import type.error.DuplicateRecordFields
import type.error.DuplicateRecordTypeFields
import type.error.DuplicateVariantTypeFields
import type.error.IllegalEmptyMatching
import type.error.IncorrectArityOfMain
import type.error.IncorrectNumberOfArguments
import type.error.MissingMain
import type.error.MissingRecordFields
import type.error.NonExhaustivePatternMatching
import type.error.NotAFunction
import type.error.NotAList
import type.error.NotARecord
import type.error.NotARef
import type.error.NotATuple
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
import type.error.UnexpectedNumberOfParametersInLambda
import type.error.UnexpectedPatternForType
import type.error.UnexpectedRecord
import type.error.UnexpectedRecordFields
import type.error.UnexpectedReference
import type.error.UnexpectedTuple
import type.error.UnexpectedTupleLength
import type.error.UnexpectedVariant
import type.error.UnexpectedVariantLabel
import type.error.withContextLayer
import type.error.withEmptyContext

@Suppress("TooManyFunctions", "LargeClass")
class TypeChecker(private val extensions: List<String>) {
  private var exceptionType: Type? = null

  private fun matchPatternWithType(pattern: Pattern, type: Type): Result<Env, ContextualTypeError> =
    when (pattern) {
      is Pattern.Variable -> Ok(mapOf(pattern.name to type))
      is Pattern.Inl ->
        binding {
          if (type !is SumType) raise(UnexpectedPatternForType(pattern, type))
          matchPatternWithType(pattern.inner, type.left).bind()
        }

      is Pattern.Inr ->
        binding {
          if (type !is SumType) raise(UnexpectedPatternForType(pattern, type))
          matchPatternWithType(pattern.inner, type.right).bind()
        }

      is Pattern.Variant ->
        binding {
          if (type !is VariantType) raise(UnexpectedPatternForType(pattern, type))
          if (pattern.label !in type.fields) raise(UnexpectedPatternForType(pattern, type))
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

  private fun BindingScope<ContextualTypeError>.checkFunctionArgTypes(
    application: Application,
    env: Env,
    expectedInputTypes: List<Type>,
  ) {
    val expectedNumberOfArguments = expectedInputTypes.size
    val actualNumberOfArguments = application.args.size
    if (actualNumberOfArguments != expectedNumberOfArguments) {
      raise(
        IncorrectNumberOfArguments(application, expectedNumberOfArguments, actualNumberOfArguments)
      )
    }
    for ((argument, expectedType) in application.args.zip(expectedInputTypes)) {
      checkType(argument, env, expectedType).bind()
    }
  }

  private fun BindingScope<ContextualTypeError>.checkLambdaParameters(
    abstraction: Abstraction,
    expectedInputTypes: List<Type>,
  ) {
    val expectedNumberOfParameters = expectedInputTypes.size
    val actualNumberOfParameters = abstraction.params.size
    if (actualNumberOfParameters != expectedNumberOfParameters) {
      raise(
        UnexpectedNumberOfParametersInLambda(
          abstraction,
          expectedNumberOfParameters,
          actualNumberOfParameters,
        )
      )
    }
    for ((parameter, expectedType) in abstraction.params.zip(expectedInputTypes)) {
      val actualType = parameter.type.toType()
      if ("#structural-subtyping" in extensions) {
        assertIsSubtypeOf(expectedType, actualType, abstraction)
      } else if (actualType != expectedType) {
        raise(UnexpectedLambdaParameterType(parameter, expectedType))
      }
    }
  }

  // a gigantic when is going to be complex, but there is no work around it
  @Suppress("CyclomaticComplexMethod", "LongMethod")
  fun checkType(expr: Expr, env: Env, expected: Type): Result<kotlin.Unit, ContextualTypeError> {
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
          }

        is TrueLiteral -> binding { assertExpectedTypeOrReport(Bool, expected, expr) }

        is FalseLiteral -> binding { assertExpectedTypeOrReport(Bool, expected, expr) }

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
          }

        is Pred ->
          binding {
            assertExpectedTypeOrReport(Nat, expected, expr)
            checkType(expr.arg, env, Nat).bind()
          }

        is Abstraction ->
          binding {
            expr.params.forEach { checkTypeDuplicates(it.type) }
            if (expected !is FunType) {
              raise(UnexpectedLambda(expr, expected))
            }
            checkLambdaParameters(expr, expected.inputTypes)
            val parameterEnv = expr.params.associate { it.name to it.type.toType() }
            checkType(expr.returnExpr, env + parameterEnv, expected.retType).bind()
          }

        is UnitConstant -> binding { assertExpectedTypeOrReport(UnitType, expected, expr) }

        is Application ->
          binding {
            val leftType = inferExprType(expr.func, env).bind()
            if (leftType !is FunType) {
              raise(NotAFunction(expr.func))
            }
            checkFunctionArgTypes(expr, env, leftType.inputTypes)
            assertExpectedTypeOrReport(leftType.retType, expected, expr)
          }

        is IntLiteral -> binding { assertExpectedTypeOrReport(Nat, expected, expr) }

        is NatRec ->
          binding {
            checkType(expr.n, env, Nat).bind()
            val initType = inferType(expr.init, env).bind()
            val expectedStepType = FunType(listOf(Nat), FunType(listOf(initType), initType))
            checkType(expr.step, env, expectedStepType).bind()
          }

        is TupleLiteral -> binding { checkTupleLiteral(expected, expr, env) }
        is TupleDotExpression ->
          binding {
            val actualType = inferExprType(expr, env).bind()
            assertExpectedTypeOrReport(actualType, expected, expr)
          }

        is RecordLiteral -> binding { checkRecordLiteralType(expected, expr, env) }

        is RecordDotExpression ->
          binding {
            val receiverType = inferType(expr.recordExpr, env).bind()
            if (receiverType !is RecordType) raise(NotARecord(expr))
            if (expr.label !in receiverType.fields)
              raise(UnexpectedFieldAccess(expr, receiverType, expr.label))
            val fieldType = receiverType.fields.getValue(expr.label)
            assertExpectedTypeOrReport(fieldType, expected, expr)
          }

        is TypeAscription ->
          binding {
            val ascribed = expr.type.toType()
            checkType(expr.expr, env, ascribed).bind()
            assertExpectedTypeOrReport(ascribed, expected, expr)
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
          }

        is ListTail ->
          binding {
            val actualType = inferExprType(expr, env).bind()
            assertExpectedTypeOrReport(actualType, expected, expr)
          }

        is ListIsEmpty ->
          binding {
            val actualType = inferExprType(expr, env).bind()
            assertExpectedTypeOrReport(actualType, expected, expr)
          }

        is Match ->
          binding {
            if (expr.cases.isEmpty()) raise(IllegalEmptyMatching(expr))
            val scrutineeType = inferExprType(expr.scrutinee, env).bind()
            // we match types before to catch the invalid match cases before checking for
            // exhaustiveness
            val patEnvs = expr.cases.map { matchPatternWithType(it.pattern, scrutineeType).bind() }
            checkExhaustiveness(expr, scrutineeType)
            for ((case, patEnv) in expr.cases.zip(patEnvs)) {
              checkType(case.expr, env + patEnv, expected).bind()
            }
          }

        is VariantLiteral ->
          binding {
            if (expected !is VariantType) raise(UnexpectedVariant(expr, expected))
            if (expr.label !in expected.fields) raise(UnexpectedVariantLabel(expr, expr.label))
            val fieldType = expected.fields.getValue(expr.label)
            checkType(expr.expr, env, fieldType).bind()
          }

        is Fix -> binding { checkType(expr.expr, env, FunType(listOf(expected), expected)).bind() }
        is Pattern.Variable -> error("unreachable")
        is Pattern.Inl -> error("unreachable")
        is Pattern.Inr -> error("unreachable")
        is Pattern.Variant -> error("unreachable")
        is Assign ->
          binding {
            val lhsType = inferExprType(expr.lhs, env).bind()
            if (lhsType !is RefType) {
              raise(NotARef(expr.lhs, lhsType))
            }
            checkType(expr.rhs, env, lhsType.inner).bind()
            assertExpectedTypeOrReport(UnitType, expected, expr)
          }

        is Deref ->
          binding {
            val inferredType = inferExprType(expr.arg, env).bind()
            if (inferredType !is RefType) {
              raise(NotARef(expr, inferredType))
            }
            assertExpectedTypeOrReport(
              actualType = inferredType,
              expected = RefType(expected),
              expr.arg,
            )
          }

        is Sequence ->
          binding {
            checkType(expr.lhs, env, UnitType).bind()
            checkType(expr.rhs, env, expected).bind()
          }

        is NewRef ->
          binding {
            if (expected !is RefType) {
              raise(UnexpectedReference(expr))
            }
            checkType(expr.initValue, env, expected.inner).bind()
          }

        is Panic ->
          binding {
            // ok, the type is as expected
          }

        is Throw -> {
          val excType = exceptionType ?: error("unspecified exception type")
          checkType(expr.arg, env, excType)
        }

        is TryWith ->
          binding {
            checkType(expr.tryExpr, env, expected).bind()
            checkType(expr.fallback, env, expected).bind()
          }

        is TryCatch ->
          binding {
            checkType(expr.tryExpr, env, expected).bind()
            val patEnvs =
              matchPatternWithType(expr.pattern, exceptionType ?: error("exception type not set"))
                .bind()
            checkType(expr.catch, env + patEnvs, expected).bind()
          }
      }
    return result.wrapWhileTypechecking(expr, expected)
  }

  private fun BindingScope<ContextualTypeError>.checkRecordLiteralType(
    expected: Type,
    expr: RecordLiteral,
    env: Env,
  ) {
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
  }

  private fun BindingScope<ContextualTypeError>.checkTupleLiteral(
    expected: Type,
    expr: TupleLiteral,
    env: Env,
  ) {
    if (expected !is TupleType) raise(UnexpectedTuple(expr, expected))
    if (expr.projections.size != expected.projections.size) {
      raise(UnexpectedTupleLength(expr, expected.projections.size, expr.projections.size))
    }
    val projectionTypes = expr.projections.map { inferType(it, env).bind() }
    val actualType = TupleType(projectionTypes)
    assertExpectedTypeOrReport(actualType, expected, expr)
  }

  private fun BindingScope<ContextualTypeError>.checkExhaustiveness(match: Match, type: Type) {
    val hasWildcard = match.cases.any { it.pattern is Pattern.Variable }
    if (hasWildcard) return
    if (type is SumType) {
      val isExhaustive =
        match.cases.any { it.pattern is Pattern.Inr } &&
          match.cases.any { it.pattern is Pattern.Inl }
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

  private fun BindingScope<ContextualTypeError>.assertIsSubtypeOf(
    subType: Type,
    superType: Type,
    expr: Expr,
  ) {
    when (superType) {
      is RecordType -> {
        if (subType !is RecordType) raise(NotARecord(expr))
        assertRecordSubtyping(subType, superType, expr)
      }

      is VariantType -> {
        if (subType !is VariantType) raise(TypeMismatch(expr, superType, subType))
        assertVariantSubtyping(subType, superType, expr)
      }

      is FunType -> {
        if (subType !is FunType) raise(TypeMismatch(expr, superType, subType))
        if (subType.inputTypes.size != superType.inputTypes.size)
          raise(TypeMismatch(expr, superType, subType))
        for ((expectedParam, actualParam) in superType.inputTypes.zip(subType.inputTypes)) {
          assertIsSubtypeOf(expectedParam, actualParam, expr)
        }
        assertIsSubtypeOf(subType.retType, superType.retType, expr)
      }

      else -> if (subType != superType) raise(TypeMismatch(expr, superType, subType))
    }
  }

  private fun BindingScope<ContextualTypeError>.assertVariantSubtyping(
    subType: VariantType,
    superType: VariantType,
    expr: Expr,
  ) {
    val extraLabels = subType.fields.keys - superType.fields.keys
    if (extraLabels.isNotEmpty()) raise(UnexpectedVariantLabel(expr, extraLabels.first()))
    for ((label, subTypeField) in subType.fields) {
      val superTypeField = superType.fields[label] ?: unreachable()
      assertIsSubtypeOf(subTypeField, superTypeField, expr)
    }
  }

  private fun BindingScope<ContextualTypeError>.assertRecordSubtyping(
    subType: RecordType,
    superType: RecordType,
    expr: Expr,
  ) {
    val superTypeKeys = superType.fields.keys
    val subTypeKeys = subType.fields.keys
    val missing = superTypeKeys - subTypeKeys
    if (missing.isNotEmpty()) raise(MissingRecordFields(expr, missing))
    for ((field, fieldType) in superType.fields) {
      val correspondingActualType = subType.fields[field] ?: unreachable()
      assertIsSubtypeOf(correspondingActualType, fieldType, expr)
    }
  }

  private fun BindingScope<ContextualTypeError>.assertExpectedTypeOrReport(
    actualType: type.Type,
    expected: type.Type,
    expr: Expr,
  ) {
    if ("#structural-subtyping" in extensions) {
      assertIsSubtypeOf(actualType, expected, expr)
    } else {
      if (actualType != expected) {
        // TODO deep check for record type
        raise(TypeMismatch(expr, expected, actualType))
      }
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

  @Suppress("CyclomaticComplexMethod")
  private fun BindingScope<ContextualTypeError>.checkTypeDuplicates(type: ast.Type) {
    when (type) {
      is ast.Type.Record -> {
        val duplicates = findDuplicateKeys(type.projections)
        if (duplicates.isNotEmpty()) raise(DuplicateRecordTypeFields(type, duplicates))
        type.projections.forEach { (_, t) -> checkTypeDuplicates(t) }
      }

      is ast.Type.Variant -> {
        val duplicates = findDuplicateKeys(type.fields.map { it.label to it.type })
        if (duplicates.isNotEmpty()) raise(DuplicateVariantTypeFields(type, duplicates))
        type.fields.forEach { checkTypeDuplicates(it.type) }
      }

      is ast.Type.Fun -> {
        type.inputTypes.forEach { checkTypeDuplicates(it) }
        checkTypeDuplicates(type.returnType)
      }

      is ast.Type.Tuple -> type.projections.forEach { checkTypeDuplicates(it) }
      is ast.Type.Sum -> {
        checkTypeDuplicates(type.left)
        checkTypeDuplicates(type.right)
      }

      is ast.Type.ListType -> checkTypeDuplicates(type.elementType)
      ast.Type.Bool,
      ast.Type.Nat,
      ast.Type.Unit -> {}

      is ast.Type.Ref -> checkTypeDuplicates(type.inner)
    }
  }

  // a gigantic when is going to be complex
  @Suppress("CyclomaticComplexMethod", "LongMethod")
  private fun inferExprType(expr: Expr, env: Env): Result<Type, ContextualTypeError> {
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
              raise(NotAFunction(expr))
            }
            checkFunctionArgTypes(expr, env, funcType.inputTypes)

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
            expr.params.forEach { checkTypeDuplicates(it.type) }
            val paramEnv = expr.params.associate { it.name to it.type.toType() }
            val updatedEnv = env + paramEnv
            val returnType = inferExprType(expr.returnExpr, updatedEnv).bind()
            FunType(expr.params.map { it.type.toType() }, returnType)
          }

        is UnitConstant -> binding { UnitType }
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
            receiverType.fields[expr.label]
              ?: raise(UnexpectedFieldAccess(expr, receiverType, expr.label))
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
            if (expr.cases.isEmpty()) raise(IllegalEmptyMatching(expr))
            val scrutineeType = inferExprType(expr.scrutinee, env).bind()
            val patEnvs = expr.cases.map { matchPatternWithType(it.pattern, scrutineeType).bind() }
            checkExhaustiveness(expr, scrutineeType)
            val resultType = inferExprType(expr.cases.first().expr, env + patEnvs.first()).bind()
            for ((case, patEnv) in expr.cases.drop(1).zip(patEnvs.drop(1))) {
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
            if (innerType.inputTypes.size != 1)
              raise(IncorrectNumberOfArguments(fixArg, 1, innerType.inputTypes.size))
            val inputType = innerType.inputTypes.single()
            if (inputType != innerType.retType) {
              raise(TypeMismatch(fixArg, FunType(listOf(inputType), inputType), innerType))
            }
            innerType.retType
          }

        is Pattern.Inl -> error("unreachable")
        is Pattern.Inr -> error("unreachable")
        is Pattern.Variant -> error("unreachable")
        is Assign -> TODO()
        is Deref -> TODO()
        is Sequence -> TODO()
        is NewRef ->
          binding {
            val inner = inferType(expr.initValue, env).bind()
            RefType(inner)
          }

        is Panic -> binding { raise(AmbiguousPanic(expr)) }
        is Throw -> binding { raise(AmbiguousThrow(expr)) }
        is TryWith ->
          binding {
            val inferred = inferType(expr.tryExpr, env).bind()
            checkType(expr.fallback, env, inferred).bind()
            inferred
          }

        is TryCatch ->
          binding {
            val inferred = inferType(expr.tryExpr, env).bind()
            val patEnvs =
              matchPatternWithType(expr.pattern, exceptionType ?: error("exception type not set"))
                .bind()
            checkType(expr.catch, env + patEnvs, inferred).bind()
            inferred
          }
      }
    return result.wrapWhileInferring(expr)
  }

  private fun BindingScope<ContextualTypeError>.resolveLocalDeclarations(
    localDecls: List<Declaration>,
    env: Env,
  ): Env {
    if (localDecls.isEmpty()) return env
    val signatures =
      localDecls.filterIsInstance<FunctionDeclaration>().associate { decl ->
        decl.name to
          FunType(decl.parameterDeclarations.map { it.type.toType() }, decl.returnType.toType())
      }
    val envWithSignatures = env + signatures
    for (decl in localDecls.filterIsInstance<FunctionDeclaration>()) {
      inferDeclType(decl, envWithSignatures).bind()
    }
    return envWithSignatures
  }

  fun inferDeclType(decl: Declaration, env: Env): Result<Type, ContextualTypeError> =
    when (decl) {
      is FunctionDeclaration ->
        binding {
          decl.parameterDeclarations.forEach { checkTypeDuplicates(it.type) }
          checkTypeDuplicates(decl.returnType)
          val paramEnv = decl.parameterDeclarations.associate { it.name to it.type.toType() }
          val updatedEnv = env + paramEnv
          val envWithLocals = resolveLocalDeclarations(decl.localDeclarations, updatedEnv)
          val returnType = decl.returnType.toType()
          checkType(decl.returnExpr, envWithLocals, returnType).bind()
          val inputParams = decl.parameterDeclarations.map { it.type.toType() }
          FunType(inputParams, returnType)
        }
      is ExceptionTypeDeclaration ->
        binding {
          exceptionType = decl.type.toType()
          type.UnitType
        }
    }

  fun inferProgramType(program: Program, env: Env): Result<Type, ContextualTypeError> = binding {
    val functionDeclarations = program.declarations.filterIsInstance<FunctionDeclaration>()
    val mainDeclaration = functionDeclarations.firstOrNull { it.name == "main" }
    if (mainDeclaration == null) {
      raise(MissingMain(program))
    }
    val actualMainArity = mainDeclaration.parameterDeclarations.size
    if (actualMainArity != 1) {
      raise(IncorrectArityOfMain(mainDeclaration, actualMainArity))
    }

    val seen = mutableSetOf<String>()
    for (decl in functionDeclarations) {
      if (!seen.add(decl.name)) {
        raise(DuplicateFunctionDeclaration(decl, decl.name))
      }
    }
    val deltaEnv =
      functionDeclarations.map {
        val type =
          FunType(
            it.parameterDeclarations.map { param -> param.type.toType() },
            it.returnType.toType(),
          )
        it.name to type
      }
    val currentEnv = env + deltaEnv
    for (declaration in program.declarations) {
      inferDeclType(declaration, currentEnv).wrapWhileInferring(program).bind()
    }
    type.UnitType
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
}
