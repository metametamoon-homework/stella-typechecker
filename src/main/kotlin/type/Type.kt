package type

sealed interface Type {
  fun prettyPrint(): String
}

data object UnitType : Type {
  override fun prettyPrint(): String = "Unit"
}

data object Nat : Type {
  override fun prettyPrint(): String = "Nat"
}

data object Bool : Type {
  override fun prettyPrint(): String = "Bool"
}

data class FunType(val inputTypes: List<Type>, val retType: Type) : Type {
  override fun prettyPrint(): String {
    val params = inputTypes.joinToString(", ") { it.prettyPrint() }
    return "fn($params) -> ${retType.prettyPrint()}"
  }
}

data class TupleType(val projections: List<Type>) : Type {
  override fun prettyPrint(): String {
    val elements = projections.joinToString(", ") { it.prettyPrint() }
    return "{$elements}"
  }
}

data class RefType(val inner: Type) : Type {
  override fun prettyPrint(): String = "&" + inner.prettyPrint()
}

data class RefSourceType(val inner: Type) : Type {
  override fun prettyPrint(): String = "&" + inner.prettyPrint()
}

data class RecordType(val fields: Map<String, Type>) : Type {
  override fun prettyPrint(): String {
    val entries = fields.entries.joinToString(", ") { (k, v) -> "$k : ${v.prettyPrint()}" }
    return "{$entries}"
  }
}

data class SumType(val left: Type, val right: Type) : Type {
  override fun prettyPrint(): String = "${left.prettyPrint()} + ${right.prettyPrint()}"
}

data class ListType(val elementType: Type) : Type {
  override fun prettyPrint(): String = "[${elementType.prettyPrint()}]"
}

data class VariantType(val fields: Map<String, Type>) : Type {
  override fun prettyPrint(): String {
    val entries =
      fields.entries.joinToString(", ") { (label, type) -> "$label : ${type.prettyPrint()}" }
    return "<| $entries |>"
  }
}

data class TypeVar(val name: String) : Type {
  override fun prettyPrint(): String = "?T${name}"
}

data class ForallType(val args: List<TypeVar>, val bodyType: Type) : Type {
  override fun prettyPrint(): String =
    "forall ${args.joinToString(", ") { it.prettyPrint() }}. ${bodyType.prettyPrint()}"
}

private var typeVarCount = 0

fun freshTypeVar(): TypeVar = TypeVar(typeVarCount++.toString())

// typealias Env = Map<String, Type>

data class Env(val vars: Map<String, Type>, val types: List<TypeVar>) {
  operator fun plus(delta: Map<String, Type>): Env = Env(vars + delta, types)

  operator fun plus(delta: List<TypeVar>): Env = Env(vars, types + delta)

  operator fun plus(other: Env): Env = Env(vars + other.vars, types + other.types)
}

val emptyEnv: Env = Env(emptyMap(), emptyList())
