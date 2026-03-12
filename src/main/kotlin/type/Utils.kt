package type

fun ast.Type.toType(): Type =
  when (this) {
    ast.Type.Bool -> Bool
    ast.Type.Nat -> Nat
    ast.Type.Unit -> type.Unit
    is ast.Type.Fun ->
      FunType(inputTypes = this.inputTypes.map { it.toType() }, this.returnType.toType())
    is ast.Type.Tuple -> TupleType(this.projections.map { it.toType() })
  }
