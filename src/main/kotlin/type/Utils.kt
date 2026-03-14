package type

fun ast.Type.toType(): Type =
  when (this) {
    ast.Type.Bool -> Bool
    ast.Type.Nat -> Nat
    ast.Type.Unit -> type.Unit
    is ast.Type.Fun ->
      FunType(inputTypes = this.inputTypes.map { it.toType() }, this.returnType.toType())
    is ast.Type.Tuple -> TupleType(this.projections.map { it.toType() })
    is ast.Type.Record -> RecordType(this.projections.mapValues { (_, v) -> v.toType() })
    is ast.Type.Sum -> SumType(left = this.left.toType(), right = this.right.toType())
  }
