package type

sealed interface Type

data object Unit : Type

data object Nat : Type

data object Bool : Type

data class FunType(val inputTypes: List<Type>, val retType: Type) : Type

typealias Env = Map<String, Type>

val emptyEnv: Env = emptyMap()
