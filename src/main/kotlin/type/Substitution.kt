package type

import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextUInt

private const val INITIAL_SEED = 1337

val rnd: Random = Random(INITIAL_SEED)

private fun uniqueName(): String {
  val prefixes = listOf("white", "red", "orange", "yellow", "green", "blue", "violet", "black")
  return prefixes[rnd.nextInt(prefixes.indices)] +
    rnd.nextUInt().toString(radix = 16).padEnd(16, '0')
}

@Suppress("CyclomaticComplexMethod")
fun Type.normalizeType(): Type =
  when (this) {
    Bool -> Bool
    is FunType -> FunType(this.inputTypes.map { it.normalizeType() }, this.retType.normalizeType())
    is ListType -> ListType(this.elementType.normalizeType())
    Nat -> Nat
    is RecordType -> RecordType(this.fields.mapValues { (_, v) -> v.normalizeType() })
    is RefSourceType -> RefSourceType(this.inner.normalizeType())
    is RefType -> RefType(this.inner.normalizeType())
    is SumType -> SumType(this.left.normalizeType(), this.right.normalizeType())
    is TupleType -> TupleType(this.projections.map { it.normalizeType() })
    is TypeVar -> this
    UnitType -> UnitType
    is VariantType -> VariantType(this.fields.mapValues { it.value.normalizeType() })
    is ForallType -> {
      val uniqueTypes = this.args.map { it to TypeVar(uniqueName()) }
      val uniqueTypesMap = uniqueTypes.toMap()
      val subst = { x: TypeVar -> uniqueTypesMap[x] ?: x }
      ForallType(
        uniqueTypes.map { it.second },
        this.bodyType.normalizeType().applySubstitutionUnsafe(subst),
      )
    }
  }

fun Type.applySubstitution(subst: Substitution): Type =
  normalizeType().applySubstitutionUnsafe(subst)

@Suppress("CyclomaticComplexMethod")
private fun Type.applySubstitutionUnsafe(subst: Substitution): Type =
  when (this) {
    Bool -> Bool
    is FunType ->
      FunType(
        this.inputTypes.map { it.applySubstitutionUnsafe(subst) },
        this.retType.applySubstitutionUnsafe(subst),
      )
    is ListType -> ListType(this.elementType.applySubstitutionUnsafe(subst))
    Nat -> Nat
    is RecordType ->
      RecordType(this.fields.mapValues { (_, v) -> v.applySubstitutionUnsafe(subst) })
    is RefSourceType -> RefSourceType(this.inner.applySubstitutionUnsafe(subst))
    is RefType -> RefType(this.inner.applySubstitutionUnsafe(subst))
    is SumType ->
      SumType(this.left.applySubstitutionUnsafe(subst), this.right.applySubstitutionUnsafe(subst))
    is TupleType -> TupleType(this.projections.map { it.applySubstitutionUnsafe(subst) })
    is TypeVar -> subst.invoke(this)
    UnitType -> UnitType
    is VariantType -> VariantType(this.fields.mapValues { it.value.applySubstitutionUnsafe(subst) })
    is ForallType ->
      ForallType(
        this.args,
        this.bodyType.applySubstitutionUnsafe(subst),
      ) // only works on normalized types
  }

fun Type.containsTypeVar(): Boolean =
  when (this) {
    is TypeVar -> true
    is FunType -> inputTypes.any { it.containsTypeVar() } || retType.containsTypeVar()
    is ListType -> elementType.containsTypeVar()
    is TupleType -> projections.any { it.containsTypeVar() }
    is RecordType -> fields.values.any { it.containsTypeVar() }
    is VariantType -> fields.values.any { it.containsTypeVar() }
    is SumType -> left.containsTypeVar() || right.containsTypeVar()
    is RefType -> inner.containsTypeVar()
    is RefSourceType -> inner.containsTypeVar()
    Bool,
    Nat,
    UnitType -> false

    is ForallType ->
      false // because we do not support type-reconstruction and universal-type simultaneous
  }
