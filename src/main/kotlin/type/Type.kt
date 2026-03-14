package type

sealed interface Type

data object Unit : Type

data object Nat : Type

data object Bool : Type

data class FunType(val inputTypes: List<Type>, val retType: Type) : Type

data class TupleType(val projections: List<Type>) : Type

data class RecordType(val fields: Map<String, Type>) : Type

data class SumType(val left: Type, val right: Type) : Type

typealias Env = Map<String, Type>

val emptyEnv: Env = emptyMap()

val defaultEnv: Env = mapOf("Nat::iszerp" to FunType(listOf(Nat), Bool))
