package type

fun ast.Type.toType(): Type =
  when (this) {
    ast.Type.Bool -> Bool
    ast.Type.Nat -> Nat
    is ast.Type.Fun ->
      FunType(inputTypes = this.inputTypes.map { it.toType() }, this.returnType.toType())
  }
