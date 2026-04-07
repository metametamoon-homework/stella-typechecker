package type

fun ast.Type.toType(): Type =
  when (this) {
    ast.Type.Bool -> Bool
    ast.Type.Nat -> Nat
    ast.Type.Unit -> Unit
    is ast.Type.Fun ->
      FunType(inputTypes = this.inputTypes.map { it.toType() }, this.returnType.toType())
    is ast.Type.Tuple -> TupleType(this.projections.map { it.toType() })
    is ast.Type.Record -> RecordType(this.projections.toMap().mapValues { (_, v) -> v.toType() })
    is ast.Type.Sum -> SumType(left = this.left.toType(), right = this.right.toType())
    is ast.Type.ListType -> ListType(elementType = this.elementType.toType())
    is ast.Type.Variant ->
      VariantType(fields = this.fields.associate { it.label to it.type.toType() })

    is ast.Type.Ref -> RefType(inner.toType())
  }
