package type

import ast.Abstraction
import ast.Application
import ast.Binding
import ast.ConsList
import ast.Declaration
import ast.Expr
import ast.FalseLiteral
import ast.Fix
import ast.FunctionDeclaration
import ast.GenericFunctionDeclaration
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
import ast.TypeAbstraction
import ast.TypeApplication
import ast.TypeAscription
import ast.UnitConst
import ast.Var
import ast.VariantLiteral
import com.github.michaelbull.result.BindingScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.annotation.UnsafeResultValueAccess
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.orElse
import kotlin.collections.plus
import type.error.AmbiguousList
import type.error.AmbiguousSumType
import type.error.AmbiguousType
import type.error.AmbiguousVariantType
import type.error.ContextualTypeError
import type.error.DuplicateFunctionDeclaration
import type.error.DuplicateRecordFields
import type.error.DuplicateRecordTypeFields
import type.error.DuplicateTypeArgs
import type.error.DuplicateVariantTypeFields
import type.error.FailedToSolveCs
import type.error.IllegalEmptyMatching
import type.error.IncorrectArityOfMain
import type.error.IncorrectNumberOfArguments
import type.error.IncorrectNumberOfTypeArgs
import type.error.MissingMain
import type.error.MissingRecordFields
import type.error.NonExhaustivePatternMatching
import type.error.NotAFunction
import type.error.NotAGeneric
import type.error.NotAList
import type.error.NotARecord
import type.error.NotATuple
import type.error.TupleIndexOutOfBound
import type.error.TypeError
import type.error.TypeErrorFrame
import type.error.TypeMismatch
import type.error.UndefinedTypeVar
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
import type.error.UnexpectedSubtype
import type.error.UnexpectedTuple
import type.error.UnexpectedTupleLength
import type.error.UnexpectedVariant
import type.error.UnexpectedVariantLabel
import type.error.UnsolvableCs
import type.error.withContextLayer
import type.error.withEmptyContext
import utils.raise

data class EqualityConstraint(val left: Type, val right: Type)

typealias TypeConstraints = List<EqualityConstraint>

data class TypeWithCs(val type: Type, val cs: TypeConstraints)

fun Type.withCs(cs: TypeConstraints): TypeWithCs = TypeWithCs(this, cs)

@Suppress("TooManyFunctions", "LargeClass")
private class TypeChecker(private val extensions: List<String>) {

  val isTypeReconstructionEnabled: Boolean = "#type-reconstruction" in extensions

  private fun matchPatternWithType(pattern: Pattern, type: Type): Result<Env, ContextualTypeError> =
    when (pattern) {
      is Pattern.Variable -> Ok(Env(mapOf(pattern.name to type), listOf()))
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
            emptyEnv
          }
        }
    }

  private fun BindingScope<ContextualTypeError>.resolveBindings(
    bindings: List<Binding>,
    env: Env,
  ): Pair<TypeConstraints, Env> {
    var currentEnv = env
    val csResult = mutableListOf<EqualityConstraint>()
    for (binding in bindings) {
      val (type, cs) = inferExprType(binding.expr, currentEnv).bind()
      csResult.addAll(cs)
      val envUpdate = matchPatternWithType(binding.pattern, type).bind()
      currentEnv = currentEnv + envUpdate
    }
    return csResult to currentEnv
  }

  private fun BindingScope<ContextualTypeError>.checkFunctionArgTypes(
    application: Application,
    env: Env,
    expectedInputTypes: List<Type>,
  ): TypeConstraints {
    val expectedNumberOfArguments = expectedInputTypes.size
    val actualNumberOfArguments = application.args.size
    if (actualNumberOfArguments != expectedNumberOfArguments) {
      raise(
        IncorrectNumberOfArguments(application, expectedNumberOfArguments, actualNumberOfArguments)
      )
    }
    val cs = mutableListOf<EqualityConstraint>()
    for ((argument, expectedType) in application.args.zip(expectedInputTypes)) {
      cs.addAll(checkType(argument, env, expectedType).bind())
    }
    return cs
  }

  private val structuralSubtypingExtension = "#structural-subtyping"

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
      if (structuralSubtypingExtension in extensions) {
        assertIsSubtypeOf(expectedType, actualType, abstraction)
      } else if (actualType != expectedType) {
        raise(UnexpectedLambdaParameterType(parameter, expectedType))
      }
    }
  }

  // a gigantic when is going to be complex, but there is no work around it
  @Suppress("CyclomaticComplexMethod", "LongMethod")
  private fun checkType(
    expr: Expr,
    env: Env,
    expected: Type,
  ): Result<TypeConstraints, ContextualTypeError> {
    @Suppress("NoNameShadowing") val expected = expected.normalizeType()
    //    if (isTypeReconstructionEnabled) {
    //      return binding {
    //        val (inferredType, cs) = inferExprType(expr, env).bind()
    //        cs + EqualityConstraint(inferredType, expected)
    //      }
    //    }
    val result: Result<TypeConstraints, ContextualTypeError> =
      when (expr) {
        is Succ ->
          binding {
            val cs1 = assertExpectedTypeOrReport(Nat, expected, expr)
            val cs2 = checkType(expr.expr, env, Nat).bind()
            cs1 + cs2
          }

        is Var ->
          binding {
            val actual = env.vars[expr.name] ?: raise(UndefinedVariable(expr))
            assertExpectedTypeOrReport(actual, expected, expr)
          }

        is TrueLiteral -> binding { assertExpectedTypeOrReport(Bool, expected, expr) }

        is FalseLiteral -> binding { assertExpectedTypeOrReport(Bool, expected, expr) }

        is IfExpression ->
          binding {
            val cs1 = checkType(expr.cond, env, Bool).bind()
            val cs2 = checkType(expr.thenBranch, env, expected).bind()
            val cs3 = checkType(expr.elseBranch, env, expected).bind()
            cs1 + cs2 + cs3
          }

        is IsZero ->
          binding {
            val cs1 = assertExpectedTypeOrReport(Bool, expected, expr)
            val cs2 = checkType(expr.arg, env, Nat).bind()
            cs1 + cs2
          }

        is Pred ->
          binding {
            val cs1 = assertExpectedTypeOrReport(Nat, expected, expr)
            val cs2 = checkType(expr.arg, env, Nat).bind()
            cs1 + cs2
          }

        is Abstraction ->
          binding {
            expr.params.forEach { checkTypeDuplicates(it.type) }
            if (expected is FunType) {
              checkLambdaParameters(expr, expected.inputTypes)
              val parameterEnv = expr.params.associate { it.name to it.type.toType() }
              checkType(expr.returnExpr, env + parameterEnv, expected.retType).bind()
            } else if (isTypeReconstructionEnabled) {
              if (expected !is TypeVar) {
                raise(UnexpectedLambda(expr, expected))
              }
              val (inferredType, cs) = inferExprType(expr, env).bind()
              cs + EqualityConstraint(inferredType, expected)
            } else {
              raise(UnexpectedLambda(expr, expected))
            }
          }

        is Application ->
          binding {
            val (leftType, leftCs) = inferExprType(expr.func, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (leftType !is FunType) {
                raise(NotAFunction(expr.func))
              }
              val argCs = checkFunctionArgTypes(expr, env, leftType.inputTypes)
              val csAss = assertExpectedTypeOrReport(leftType.retType, expected, expr)
              leftCs + argCs + csAss
            } else {
              if (leftType !is FunType && leftType !is TypeVar) {
                raise(NotAFunction(expr.func))
              }
              val argTypes = expr.args.map { inferType(it, env).bind() }
              val resultTypeVar = freshTypeVar()
              val deducedFromArgsFunType = FunType(argTypes.map { it.type }, resultTypeVar)
              argTypes.flatMap { it.cs } +
                leftCs +
                listOf(
                  EqualityConstraint(expected, resultTypeVar),
                  EqualityConstraint(leftType, deducedFromArgsFunType),
                )
            }
          }

        is IntLiteral -> binding { assertExpectedTypeOrReport(Nat, expected, expr) }

        is NatRec ->
          binding {
            checkType(expr.n, env, Nat).bind()
            val (initType, initCs) = inferType(expr.init, env).bind()
            val expectedStepType = FunType(listOf(Nat), FunType(listOf(initType), initType))
            val stepCs = checkType(expr.step, env, expectedStepType).bind()
            val csAss = assertExpectedTypeOrReport(initType, expected, expr)
            stepCs + initCs + csAss
          }

        is TupleLiteral -> binding { checkTupleLiteral(expected, expr, env) }
        is TupleDotExpression ->
          binding {
            val (actualType, cs) = inferExprType(expr, env).bind()
            val csAss = assertExpectedTypeOrReport(actualType, expected, expr)
            cs + csAss
          }

        is RecordLiteral -> binding { checkRecordLiteralType(expected, expr, env) }

        is RecordDotExpression ->
          binding {
            val (receiverType, cs) = inferType(expr.recordExpr, env).bind()
            if (receiverType !is RecordType) raise(NotARecord(expr))
            if (expr.label !in receiverType.fields)
              raise(UnexpectedFieldAccess(expr, receiverType, expr.label))
            val fieldType = receiverType.fields.getValue(expr.label)
            val csAss = assertExpectedTypeOrReport(fieldType, expected, expr)
            cs + csAss
          }

        is TypeAscription ->
          binding {
            val ascribed = expr.type.toType()
            val cs = checkType(expr.expr, env, ascribed).bind()
            val csAss = assertExpectedTypeOrReport(ascribed, expected, expr)
            cs + csAss
          }

        is LetBinding ->
          binding {
            val (cs, updatedEnv) = resolveBindings(expr.bindings, env)
            val cs2 = checkType(expr.body, updatedEnv, expected).bind()
            cs + cs2
          }

        is Inl ->
          binding {
            if (expected is SumType) {
              checkType(expr.expr, env, expected.left).bind()
            } else if (isTypeReconstructionEnabled) {
              val (inferredType, cs) = inferExprType(expr, env).bind()
              cs + EqualityConstraint(inferredType, expected)
            } else {
              raise(UnexpectedInjection(expr, expected))
            }
          }

        is Inr ->
          binding {
            if (expected is SumType) {
              checkType(expr.expr, env, expected.right).bind()
            } else if (isTypeReconstructionEnabled) {
              val (inferredType, cs) = inferExprType(expr, env).bind()
              cs + EqualityConstraint(inferredType, expected)
            } else {
              raise(UnexpectedInjection(expr, expected))
            }
          }

        is ListLiteral ->
          binding {
            if (!isTypeReconstructionEnabled) {
              if (expected !is ListType) raise(UnexpectedList(expr, expected))
              val cs = mutableListOf<EqualityConstraint>()
              for (element in expr.elements) {
                cs.addAll(checkType(element, env, expected.elementType).bind())
              }
              cs
            } else {
              val cs = mutableListOf<EqualityConstraint>()
              val resultType = freshTypeVar()
              for (element in expr.elements) {
                val (type, csLocal) = inferType(element, env).bind()
                cs.addAll(csLocal)
                cs.add(EqualityConstraint(type, resultType))
              }
              cs.add(EqualityConstraint(expected, resultType))
              cs
            }
          }

        is ConsList ->
          binding {
            if (expected is ListType) {
              val headCs = checkType(expr.head, env, expected.elementType).bind()
              val tailCs = checkType(expr.tail, env, expected).bind()
              headCs + tailCs
            } else if (isTypeReconstructionEnabled) {
              if (expected !is TypeVar) raise(UnexpectedList(expr, expected))
              val (inferredType, cs) = inferExprType(expr, env).bind()
              cs + EqualityConstraint(inferredType, expected)
            } else raise(UnexpectedList(expr, expected))
          }

        is ListHead -> binding { checkType(expr.list, env, ListType(expected)).bind() }

        is ListTail ->
          binding {
            val (actualType, cs) = inferExprType(expr, env).bind()
            val csAss = assertExpectedTypeOrReport(actualType, expected, expr)
            cs + csAss
          }

        is ListIsEmpty ->
          binding {
            val (actualType, cs) = inferExprType(expr, env).bind()
            val csAss = assertExpectedTypeOrReport(actualType, expected, expr)
            cs + csAss
          }

        is Match ->
          binding {
            val resultCs = mutableListOf<EqualityConstraint>()
            if (expr.cases.isEmpty()) raise(IllegalEmptyMatching(expr))
            val (scrutineeType, cs) = inferExprType(expr.scrutinee, env).bind()
            resultCs.addAll(cs)
            // we match types before to catch the invalid match cases before checking for
            // exhaustiveness
            val patEnvs = expr.cases.map { matchPatternWithType(it.pattern, scrutineeType).bind() }
            checkExhaustiveness(expr, scrutineeType)
            for ((case, patEnv) in expr.cases.zip(patEnvs)) {
              val localCs = checkType(case.expr, env + patEnv, expected).bind()
              resultCs.addAll(localCs)
            }
            resultCs
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
        is TypeApplication ->
          binding {
            val (inferredType, cs) = inferType(expr, env).bind()
            assertExpectedTypeOrReport(inferredType, expected, expr) + cs
          }

        is TypeAbstraction ->
          binding {
            val (inferredType, cs) = inferType(expr, env).bind()
            assertExpectedTypeOrReport(inferredType, expected, expr) + cs
          }
        is UnitConst -> binding { assertExpectedTypeOrReport(UnitType, expected, expr) }
      }
    return result.wrapWhileTypechecking(expr, expected)
  }

  private fun BindingScope<ContextualTypeError>.checkRecordLiteralType(
    expected: Type,
    expr: RecordLiteral,
    env: Env,
  ): TypeConstraints {
    if (isTypeReconstructionEnabled) {
      // give up in record type
      val (inferredType, cs) = inferExprType(expr, env).bind()
      return cs + EqualityConstraint(inferredType, expected)
    }
    if (expected !is RecordType) raise(UnexpectedRecord(expr, expected))
    val duplicates = findDuplicateKeys(expr.bindings)
    if (duplicates.isNotEmpty()) raise(DuplicateRecordFields(expr, duplicates))
    val bindingsMap = expr.bindings.toMap()
    val expectedKeys = expected.fields.keys
    val actualKeys = bindingsMap.keys
    val missing = expectedKeys - actualKeys
    val extra = actualKeys - expectedKeys
    if (missing.isNotEmpty()) raise(MissingRecordFields(expr, missing))
    if (extra.isNotEmpty() && structuralSubtypingExtension !in extensions)
      raise(UnexpectedRecordFields(expr, extra))
    val cs = mutableListOf<EqualityConstraint>()
    for ((field, expectedFieldType) in expected.fields) {
      cs.addAll(checkType(bindingsMap.getValue(field), env, expectedFieldType).bind())
    }
    return cs
  }

  private fun BindingScope<ContextualTypeError>.checkTupleLiteral(
    expected: Type,
    expr: TupleLiteral,
    env: Env,
  ): TypeConstraints {
    if (expected is TupleType) {
      if (expr.projections.size != expected.projections.size) {
        raise(UnexpectedTupleLength(expr, expected.projections.size, expr.projections.size))
      }
      val cs = mutableListOf<EqualityConstraint>()
      for ((element, expectedElementType) in expr.projections.zip(expected.projections)) {
        cs.addAll(checkType(element, env, expectedElementType).bind())
      }
      return cs
    } else if (isTypeReconstructionEnabled && expected is TypeVar) {
      val (inferredType, cs) = inferExprType(expr, env).bind()
      return cs + EqualityConstraint(inferredType, expected)
    } else {
      raise(UnexpectedTuple(expr, expected))
    }
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

  @Suppress("CyclomaticComplexMethod")
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
      is ListType -> {
        if (subType !is ListType) raise(TypeMismatch(expr, superType, subType))
        assertIsSubtypeOf(subType.elementType, superType.elementType, expr)
      }

      is RefType -> {
        if (subType !is RefType) raise(TypeMismatch(expr, superType, subType))
        assertIsSubtypeOf(subType.inner, superType.inner, expr)
        assertIsSubtypeOf(superType.inner, subType.inner, expr)
      }
      is RefSourceType -> {
        when (subType) {
          is RefType -> assertIsSubtypeOf(subType.inner, superType.inner, expr)
          is RefSourceType -> assertIsSubtypeOf(subType.inner, superType.inner, expr)
          else -> raise(TypeMismatch(expr, superType, subType))
        }
      }

      is TupleType -> {
        if (subType !is TupleType) raise(TypeMismatch(expr, superType, subType))
        if (subType.projections.size != superType.projections.size)
          raise(TypeMismatch(expr, superType, subType))
        for ((projSubType, projSuperType) in subType.projections.zip(superType.projections)) {
          assertIsSubtypeOf(projSubType, projSuperType, expr)
        }
      }
      is SumType -> {
        if (subType !is SumType) raise(TypeMismatch(expr, superType, subType))
        assertIsSubtypeOf(subType.left, superType.left, expr)
        assertIsSubtypeOf(subType.right, superType.right, expr)
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

      else -> if (subType != superType) raise(UnexpectedSubtype(expr, superType, subType))
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
    actualType: Type,
    expected: Type,
    expr: Expr,
  ): List<EqualityConstraint> {
    if (structuralSubtypingExtension in extensions) {
      assertIsSubtypeOf(actualType, expected, expr)
    } else {
      if (isTypeReconstructionEnabled) {
        return listOf(EqualityConstraint(actualType, expected))
      } else if (actualType is ForallType) {
        if (expected !is ForallType) {
          raise(TypeMismatch(expr, expected, actualType))
        }
        if (actualType.args.size != expected.args.size) {
          raise(TypeMismatch(expr, expected, actualType))
        }
        val expNorm = expected.normalizeType() as ForallType
        val actNorm = actualType.normalizeType() as ForallType
        val adaptedActBody =
          actNorm.bodyType.applySubstitution { x ->
            if (x in actNorm.args) {
              val idx = actNorm.args.indexOf(x)
              expNorm.args[idx]
            } else {
              x
            }
          }
        return assertExpectedTypeOrReport(adaptedActBody, expNorm.bodyType, expr)
      } else if (actualType != expected) {
        // TODO deep check for record type
        raise(TypeMismatch(expr, expected, actualType))
      }
    }
    return emptyList()
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
      is ast.Type.Bool,
      is ast.Type.Nat,
      is ast.Type.Unit -> {}

      is ast.Type.Ref -> checkTypeDuplicates(type.inner)

      is ast.Type.Auto -> {}
      is ast.Type.TypeVar -> {}
      is ast.Type.ForAll -> {}
    }
  }

  @Suppress("CyclomaticComplexMethod", "LongMethod")
  private fun inferExprType(expr: Expr, env: Env): Result<TypeWithCs, ContextualTypeError> {
    val result: Result<TypeWithCs, ContextualTypeError> =
      when (expr) {
        is Succ ->
          binding {
            val cs = checkType(expr.expr, env, Nat).bind()
            Nat.withCs(cs)
          }

        is Var ->
          binding {
            val actual = env.vars[expr.name] ?: raise(UndefinedVariable(expr))
            actual.withCs(emptyList())
          }

        is Application ->
          binding {
            val (leftType, leftCs) = inferExprType(expr.func, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (leftType !is FunType) {
                raise(NotAFunction(expr))
              }
              val argCs = checkFunctionArgTypes(expr, env, leftType.inputTypes)
              leftType.retType.withCs(leftCs + argCs)
            } else {
              if (leftType !is FunType && leftType !is TypeVar) {
                raise(NotAFunction(expr))
              }
              val argTypes = expr.args.map { inferType(it, env).bind() }
              val resultTypeVar = freshTypeVar()
              val deducedFromArgsFunType = FunType(argTypes.map { it.type }, resultTypeVar)
              val cs =
                argTypes.flatMap { it.cs } +
                  leftCs +
                  listOf(EqualityConstraint(leftType, deducedFromArgsFunType))
              resultTypeVar.withCs(cs)
            }
          }

        is TrueLiteral -> binding { Bool.withCs(emptyList()) }
        is FalseLiteral -> binding { Bool.withCs(emptyList()) }

        is IfExpression ->
          binding {
            val condCs = checkType(expr.cond, env, Bool).bind()
            val (inferredType, thenCs) = inferExprType(expr.thenBranch, env).bind()
            val elseCs = checkType(expr.elseBranch, env, inferredType).bind()
            inferredType.withCs(condCs + thenCs + elseCs)
          }

        is IsZero ->
          binding {
            val cs = checkType(expr.arg, env, Nat).bind()
            Bool.withCs(cs)
          }

        is Pred ->
          binding {
            val cs = checkType(expr.arg, env, Nat).bind()
            Nat.withCs(cs)
          }

        is NatRec ->
          binding {
            val nCs = checkType(expr.n, env, Nat).bind()
            val (exprType, initCs) = inferExprType(expr.init, env).bind()
            val stepCs =
              checkType(expr.step, env, FunType(listOf(Nat), FunType(listOf(exprType), exprType)))
                .bind()
            exprType.withCs(nCs + initCs + stepCs)
          }

        is IntLiteral -> binding { Nat.withCs(emptyList()) }
        is Abstraction ->
          binding {
            expr.params.forEach { checkTypeDuplicates(it.type) }
            val paramEnv = expr.params.associate { it.name to it.type.toType() }
            val updatedEnv = env + paramEnv
            val (returnType, retCs) = inferExprType(expr.returnExpr, updatedEnv).bind()
            FunType(expr.params.map { it.type.toType() }, returnType).withCs(retCs)
          }

        is TupleLiteral ->
          binding {
            val projectionTypes = expr.projections.map { inferType(it, env).bind() }
            val types = projectionTypes.map { it.type }
            val cs = projectionTypes.flatMap { it.cs }
            TupleType(types).withCs(cs)
          }

        is TupleDotExpression ->
          binding {
            val (receiverType, cs) = inferType(expr.tupleExpr, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (receiverType !is TupleType) {
                raise(NotATuple(expr))
              }
              if (receiverType.projections.size < expr.index) {
                raise(TupleIndexOutOfBound(expr))
              }
              receiverType.projections[expr.index - 1].withCs(cs)
            } else {
              val lhs = freshTypeVar()
              val rhs = freshTypeVar()
              val pairType = TupleType(listOf(lhs, rhs))
              if (expr.index == 1) {
                lhs.withCs(cs + EqualityConstraint(receiverType, pairType))
              } else if (expr.index == 2) {
                rhs.withCs(cs + EqualityConstraint(receiverType, pairType))
              } else {
                raise(TupleIndexOutOfBound(expr))
              }
            }
          }

        is RecordLiteral ->
          binding {
            val duplicates = findDuplicateKeys(expr.bindings)
            if (duplicates.isNotEmpty()) raise(DuplicateRecordFields(expr, duplicates))
            val fieldResults =
              expr.bindings.toMap().mapValues { entry -> inferType(entry.value, env).bind() }
            val types = fieldResults.mapValues { it.value.type }
            val cs = fieldResults.values.flatMap { it.cs }
            RecordType(types).withCs(cs)
          }

        is RecordDotExpression ->
          binding {
            val (receiverType, cs) = inferType(expr.recordExpr, env).bind()
            if (receiverType !is RecordType) {
              raise(NotARecord(expr))
            }
            val fieldType =
              receiverType.fields[expr.label]
                ?: raise(UnexpectedFieldAccess(expr, receiverType, expr.label))
            fieldType.withCs(cs)
          }

        is TypeAscription ->
          binding {
            val ascribed = expr.type.toType()
            val cs = checkType(expr.expr, env, ascribed).bind()
            ascribed.withCs(cs)
          }

        is LetBinding ->
          binding {
            val (cs, updatedEnv) = resolveBindings(expr.bindings, env)
            val (bodyType, bodyCs) = inferExprType(expr.body, updatedEnv).bind()
            bodyType.withCs(cs + bodyCs)
          }

        is Pattern.Variable -> error("unreachable")
        is ListLiteral ->
          binding {
            if (expr.elements.isEmpty()) {
              if (isTypeReconstructionEnabled) {
                ListType(freshTypeVar()).withCs(emptyList())
              } else {
                raise(AmbiguousList(expr))
              }
            } else {
              val (firstType, firstCs) = inferExprType(expr.elements.first(), env).bind()
              val restCs = mutableListOf<EqualityConstraint>()
              for (element in expr.elements.drop(1)) {
                restCs.addAll(checkType(element, env, firstType).bind())
              }
              ListType(firstType).withCs(firstCs + restCs)
            }
          }

        is ConsList ->
          binding {
            val (headType, headCs) = inferExprType(expr.head, env).bind()
            val tailExpected = ListType(headType)
            val tailCs = checkType(expr.tail, env, tailExpected).bind()
            tailExpected.withCs(headCs + tailCs)
          }

        is ListHead ->
          binding {
            val (listType, cs) = inferExprType(expr.list, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (listType !is ListType) raise(NotAList(expr.list))
              listType.elementType.withCs(cs)
            } else {
              val freshVar = freshTypeVar()
              freshVar.withCs(cs + EqualityConstraint(listType, ListType(freshVar)))
            }
          }

        is ListTail ->
          binding {
            val (listType, cs) = inferExprType(expr.list, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (listType !is ListType) raise(NotAList(expr.list))
              listType.withCs(cs)
            } else {
              val freshVar = freshTypeVar()
              ListType(freshVar).withCs(cs + EqualityConstraint(listType, ListType(freshVar)))
            }
          }

        is ListIsEmpty ->
          binding {
            val (listType, cs) = inferExprType(expr.list, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (listType !is ListType) raise(NotAList(expr.list))
              Bool.withCs(cs)
            } else {
              Bool.withCs(cs + EqualityConstraint(listType, ListType(freshTypeVar())))
            }
          }

        is Inl ->
          binding {
            if (isTypeReconstructionEnabled) {
              val (inferredInjType, cs) = inferType(expr.expr, env).bind()
              val freshVar = freshTypeVar()
              SumType(inferredInjType, freshVar).withCs(cs)
            } else {
              raise(AmbiguousSumType(expr))
            }
          }
        is Inr ->
          binding {
            if (isTypeReconstructionEnabled) {
              val (inferredInjType, cs) = inferType(expr.expr, env).bind()
              val freshVar = freshTypeVar()
              SumType(freshVar, inferredInjType).withCs(cs)
            } else {
              raise(AmbiguousSumType(expr))
            }
          }

        is Match ->
          binding {
            if (expr.cases.isEmpty()) raise(IllegalEmptyMatching(expr))
            val (scrutineeType, scrutineeCs) = inferExprType(expr.scrutinee, env).bind()
            val patEnvs = expr.cases.map { matchPatternWithType(it.pattern, scrutineeType).bind() }
            checkExhaustiveness(expr, scrutineeType)
            val (resultType, resultCs) =
              inferExprType(expr.cases.first().expr, env + patEnvs.first()).bind()
            val restCs = mutableListOf<EqualityConstraint>()
            for ((c, patEnv) in expr.cases.drop(1).zip(patEnvs.drop(1))) {
              restCs.addAll(checkType(c.expr, env + patEnv, resultType).bind())
            }
            resultType.withCs(scrutineeCs + resultCs + restCs)
          }

        is VariantLiteral -> Err(AmbiguousVariantType(expr).withEmptyContext())
        is Fix ->
          binding {
            val fixArg = expr.expr
            val (innerType, cs) = inferExprType(fixArg, env).bind()
            if (!isTypeReconstructionEnabled) {
              if (innerType !is FunType) raise(NotAFunction(fixArg))
              if (innerType.inputTypes.size != 1)
                raise(IncorrectNumberOfArguments(fixArg, 1, innerType.inputTypes.size))
              val inputType = innerType.inputTypes.single()
              if (inputType != innerType.retType) {
                raise(TypeMismatch(fixArg, FunType(listOf(inputType), inputType), innerType))
              }
              innerType.retType.withCs(cs)
            } else {
              val resultType = freshTypeVar()
              resultType.withCs(
                cs + EqualityConstraint(innerType, FunType(listOf(resultType), resultType))
              )
            }
          }

        is Pattern.Inl -> error("unreachable")
        is Pattern.Inr -> error("unreachable")
        is Pattern.Variant -> error("unreachable")
        is TypeApplication ->
          binding {
            val (funcType, cs) = inferType(expr.func, env).bind()
            for (arg in expr.args) {
              val fredVars = arg.extractFreeVars()
              val firstMismatch =
                fredVars.firstOrNull { freeVar -> freeVar.name !in env.types.map { it.name } }
              if (firstMismatch != null) {
                raise(UndefinedTypeVar(firstMismatch))
              }
            }
            val args = expr.args.map { it.toType() }
            if (funcType !is ForallType) {
              raise(NotAGeneric(funcType, expr))
            }
            if (funcType.args.size != expr.args.size) {
              raise(IncorrectNumberOfTypeArgs(expr))
            }
            val sbs = { x: TypeVar ->
              if (x in funcType.args) {
                val idx = funcType.args.indexOf(x)
                args[idx]
              } else {
                x
              }
            }
            val resultType = funcType.bodyType.applySubstitution(sbs)
            resultType.withCs(cs)
          }

        is TypeAbstraction ->
          binding {
            val typeArgs = expr.typeArgs.map { it.toType() as TypeVar }
            if (typeArgs.toSet().size < typeArgs.size) {
              raise(DuplicateTypeArgs(expr))
            }
            val newEnv = env + typeArgs
            val (inferredType, cs) = inferType(expr.body, newEnv).bind()
            ForallType(typeArgs, inferredType).withCs(cs)
          }

        is UnitConst -> binding { UnitType.withCs(emptyList()) }
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
    val genericFunctionDeclarations = localDecls.filterIsInstance<GenericFunctionDeclaration>()

    val genericSigs =
      genericFunctionDeclarations.associate { genericDecl ->
        val funType =
          FunType(
            genericDecl.parameterDeclarations.map { param -> param.type.toType() },
            genericDecl.returnType.toType(),
          )
        val type = ForallType(genericDecl.generics.map { it.toType() as TypeVar }, funType)
        genericDecl.name to type
      }

    val envWithSignatures = env + signatures + genericSigs
    for (decl in
      localDecls.filterIsInstance<FunctionDeclaration>() +
        localDecls.filterIsInstance<GenericFunctionDeclaration>()) {
      inferDeclType(decl, envWithSignatures).bind()
    }
    return envWithSignatures
  }

  private fun ast.Type.extractFreeVars(): Set<ast.Type.TypeVar> {
    return when (this) {
      is ast.Type.Auto -> emptySet()
      is ast.Type.Bool -> emptySet()
      is ast.Type.ForAll ->
        body
          .extractFreeVars()
          .filter { freeVar -> freeVar.name !in this.bindings.map { it.name } }
          .toSet()
      is ast.Type.Fun ->
        this.inputTypes.flatMap { it.extractFreeVars() }.toSet() + this.returnType.extractFreeVars()
      is ast.Type.ListType -> this.elementType.extractFreeVars()
      is ast.Type.Nat -> emptySet()
      is ast.Type.Record -> this.projections.flatMap { it.second.extractFreeVars() }.toSet()
      is ast.Type.Ref -> this.inner.extractFreeVars()
      is ast.Type.Sum -> this.left.extractFreeVars() + this.right.extractFreeVars()
      is ast.Type.Tuple -> this.projections.flatMap { it.extractFreeVars() }.toSet()
      is ast.Type.TypeVar -> setOf(this)
      is ast.Type.Unit -> emptySet()
      is ast.Type.Variant -> this.fields.flatMap { it.type.extractFreeVars() }.toSet()
    }
  }

  fun inferDeclType(decl: Declaration, env: Env): Result<TypeWithCs, ContextualTypeError> =
    when (decl) {
      is FunctionDeclaration ->
        binding {
          decl.parameterDeclarations.forEach { checkTypeDuplicates(it.type) }
          decl.parameterDeclarations.forEach { paramDecl ->
            val firstMismatch = paramDecl.type.extractFreeVars().firstOrNull()
            if (firstMismatch != null) {
              raise(UndefinedTypeVar(firstMismatch))
            }
          }
          run {
            val firstMismatch = decl.returnType.extractFreeVars().firstOrNull()
            if (firstMismatch != null) {
              raise(UndefinedTypeVar(firstMismatch))
            }
          }
          checkTypeDuplicates(decl.returnType)
          val paramEnv = decl.parameterDeclarations.associate { it.name to it.type.toType() }
          val updatedEnv = env + paramEnv
          val envWithLocals = resolveLocalDeclarations(decl.localDeclarations, updatedEnv)
          val returnType = decl.returnType.toType()
          val cs = checkType(decl.returnExpr, envWithLocals, returnType).bind()
          val inputParams = decl.parameterDeclarations.map { it.type.toType() }
          FunType(inputParams, returnType).withCs(cs)
        }

      is GenericFunctionDeclaration ->
        binding {
          val allowedTypes = decl.generics
          decl.parameterDeclarations.forEach { checkTypeDuplicates(it.type) }
          checkTypeDuplicates(decl.returnType)
          val paramEnv =
            decl.parameterDeclarations.associate { paramDecl ->
              val firstMismatch =
                paramDecl.type.extractFreeVars().firstOrNull { freeTypeVar ->
                  freeTypeVar.name !in allowedTypes.map { it.name }
                }
              if (firstMismatch != null) {
                raise(UndefinedTypeVar(firstMismatch))
              }
              paramDecl.name to paramDecl.type.toType()
            }
          run {
            val firstMismatch =
              decl.returnType.extractFreeVars().firstOrNull { freeTypeVar ->
                freeTypeVar.name !in allowedTypes.map { it.name }
              }
            if (firstMismatch != null) {
              raise(UndefinedTypeVar(firstMismatch))
            }
          }
          val updatedEnv = env + paramEnv + allowedTypes.map { it.toType() as TypeVar }
          val envWithLocals = resolveLocalDeclarations(decl.localDeclarations, updatedEnv)
          val returnType = decl.returnType.toType()
          val cs = checkType(decl.returnExpr, envWithLocals, returnType).bind()
          val inputParams = decl.parameterDeclarations.map { it.type.toType() }
          ForallType(allowedTypes.map { it.toType() as TypeVar }, FunType(inputParams, returnType))
            .withCs(cs)
        }
    }

  fun inferProgramType(program: Program, env: Env): Result<TypeWithCs, ContextualTypeError> =
    binding {
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

      val genericFunctionDeclarations =
        program.declarations.filterIsInstance<GenericFunctionDeclaration>()

      for (decl in genericFunctionDeclarations) {
        if (!seen.add(decl.name)) {
          raise(DuplicateFunctionDeclaration(decl, decl.name))
        }
        if (decl.generics.map { it.name }.toSet().size < decl.generics.size) {
          raise(DuplicateTypeArgs(decl))
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
        } +
          genericFunctionDeclarations.map { genericDecl ->
            val funType =
              FunType(
                genericDecl.parameterDeclarations.map { param -> param.type.toType() },
                genericDecl.returnType.toType(),
              )
            val type = ForallType(genericDecl.generics.map { it.toType() as TypeVar }, funType)
            genericDecl.name to type
          }
      val currentEnv = env + deltaEnv.toMap()
      val cs = mutableListOf<EqualityConstraint>()
      for (declaration in program.declarations) {
        cs.addAll(inferDeclType(declaration, currentEnv).wrapWhileInferring(program).bind().cs)
      }
      UnitType.withCs(cs)
    }

  fun inferType(node: Node, env: Env): Result<TypeWithCs, ContextualTypeError> =
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

typealias Substitution = (TypeVar) -> Type

inline fun <reified T> tryPutFirst(arg: EqualityConstraint): Pair<T, Type>? {
  return if (arg.left is T) arg.left to arg.right
  else if (arg.right is T) arg.right to arg.left else null
}

sealed interface UnifyError

data object Failed : UnifyError

data object InfType : UnifyError

private fun Type.extractFreeVars(): Set<TypeVar> {
  return when (this) {
    Bool -> setOf()
    is ForallType -> bodyType.extractFreeVars().subtract(args.toSet())
    is FunType -> retType.extractFreeVars() + inputTypes.flatMap { it.extractFreeVars() }.toSet()
    is ListType -> elementType.extractFreeVars()
    Nat -> setOf()
    is RecordType -> fields.values.flatMap { it.extractFreeVars() }.toSet()
    is RefSourceType -> inner.extractFreeVars()
    is RefType -> inner.extractFreeVars()
    is SumType -> left.extractFreeVars() + right.extractFreeVars()
    is TupleType -> projections.flatMap { it.extractFreeVars() }.toSet()
    is TypeVar -> setOf(this)
    UnitType -> setOf()
    is VariantType -> fields.values.flatMap { it.extractFreeVars() }.toSet()
  }
}

@Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
fun unify(cs: TypeConstraints): Result<Substitution, UnifyError> = binding {
  val head = cs.firstOrNull() ?: return@binding { it }
  val tail = cs.subList(1, cs.size)
  if (head.left == head.right) {
    return@binding unify(tail).bind()
  }
  val normHead = if (head.right is TypeVar) EqualityConstraint(head.right, head.left) else head
  if (normHead.left is TypeVar) {
    val rightFreeVars = normHead.right.extractFreeVars()
    if (normHead.left in rightFreeVars) {
      raise(InfType)
    }
    val sbs: Substitution = { x: TypeVar -> if (x == normHead.left) normHead.right else x }
    val sbsTail =
      tail.map {
        EqualityConstraint(it.left.applySubstitution(sbs), it.right.applySubstitution(sbs))
      }
    val subResult = unify(sbsTail).bind()
    return@binding { x -> x.applySubstitution(sbs).applySubstitution(subResult) }
  } else if (head.left is Nat) {
    if (head.right !is Nat) {
      raise(Failed)
    }
    return@binding unify(tail).bind()
  } else if (head.left is Bool) {
    if (head.right !is Bool) {
      raise(Failed)
    }
    return@binding unify(tail).bind()
  } else if (head.left is UnitType) {
    if (head.right !is UnitType) {
      raise(Failed)
    }
    return@binding unify(tail).bind()
  }
  tryPutFirst<FunType>(head)?.let { (left, right) ->
    if (right !is FunType) raise(Failed)
    if (left.inputTypes.size != right.inputTypes.size) raise(Failed)
    val newCs =
      left.inputTypes.zip(right.inputTypes) { x, y -> EqualityConstraint(x, y) } +
        EqualityConstraint(left.retType, right.retType)
    return@binding unify(tail + newCs).bind()
  }
  tryPutFirst<TupleType>(head)?.let { (left, right) ->
    if (right !is TupleType) raise(Failed)
    if (left.projections.size != right.projections.size) raise(Failed)
    val newCs = left.projections.zip(right.projections) { x, y -> EqualityConstraint(x, y) }
    return@binding unify(tail + newCs).bind()
  }
  tryPutFirst<RefType>(head)?.let { (left, right) ->
    if (right !is RefType) raise(Failed)
    return@binding unify(tail + EqualityConstraint(left.inner, right.inner)).bind()
  }
  tryPutFirst<RefSourceType>(head)?.let { (left, right) ->
    if (right !is RefSourceType) raise(Failed)
    return@binding unify(tail + EqualityConstraint(left.inner, right.inner)).bind()
  }
  tryPutFirst<SumType>(head)?.let { (left, right) ->
    if (right !is SumType) raise(Failed)
    val newCs =
      listOf(EqualityConstraint(left.left, right.left), EqualityConstraint(left.right, right.right))
    return@binding unify(tail + newCs).bind()
  }
  tryPutFirst<ListType>(head)?.let { (left, right) ->
    if (right !is ListType) raise(Failed)
    return@binding unify(tail + EqualityConstraint(left.elementType, right.elementType)).bind()
  }
  tryPutFirst<RecordType>(head)?.let { (left, right) ->
    if (right !is RecordType) raise(Failed)
    if (left.fields.keys != right.fields.keys) raise(Failed)
    val newCs = left.fields.map { (k, v) -> EqualityConstraint(v, right.fields.getValue(k)) }
    return@binding unify(tail + newCs).bind()
  }
  tryPutFirst<VariantType>(head)?.let { (left, right) ->
    if (right !is VariantType) raise(Failed)
    if (left.fields.keys != right.fields.keys) raise(Failed)
    val newCs = left.fields.map { (k, v) -> EqualityConstraint(v, right.fields.getValue(k)) }
    return@binding unify(tail + newCs).bind()
  }

  TODO()
}

@OptIn(UnsafeResultValueAccess::class)
fun inferTypeApi(program: Program): Result<Type, ContextualTypeError> = binding {
  val (type, cs) = TypeChecker(program.extensions).inferType(program, emptyEnv).bind()
  val unifier =
    unify(cs)
      .orElse<(TypeVar) -> Type, UnifyError, Nothing> {
        if (it is Failed) {
          raise(FailedToSolveCs(program).withEmptyContext())
        } else {
          raise(UnsolvableCs(cs, program).withEmptyContext())
        }
      }
      .value
  val s =
    cs
      .flatMap { listOf(it.left, it.right) }
      .filter { it.applySubstitution(unifier).containsTypeVar() }
  if (s.isNotEmpty()) {
    raise(AmbiguousType(program).withEmptyContext())
  }
  val resultType = type.applySubstitution(unifier)
  resultType
}
