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

data object Top : Type {
  override fun prettyPrint(): String = "Top"
}

data object Bot : Type {
  override fun prettyPrint(): String = "Bot"
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

typealias Env = Map<String, Type>

val emptyEnv: Env = emptyMap()

val defaultEnv: Env = mapOf("Nat::iszerp" to FunType(listOf(Nat), Bool))
