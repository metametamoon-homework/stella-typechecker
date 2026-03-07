package type

sealed interface Type

data object Nat : Type

data object Bool : Type

data class FunType(val inputTypes: List<Type>, val retType: Type) : Type

typealias Env = Map<String, Type>
