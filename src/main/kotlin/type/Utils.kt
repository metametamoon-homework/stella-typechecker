package type

fun ast.Type.toType(): Type =
  when (this) {
    is ast.Type.Bool -> Bool
    is ast.Type.Nat -> Nat
    is ast.Type.Unit -> UnitType
    is ast.Type.Fun ->
      FunType(inputTypes = this.inputTypes.map { it.toType() }, this.returnType.toType())
    is ast.Type.Tuple -> TupleType(this.projections.map { it.toType() })
    is ast.Type.Record -> RecordType(this.projections.toMap().mapValues { (_, v) -> v.toType() })
    is ast.Type.Sum -> SumType(left = this.left.toType(), right = this.right.toType())
    is ast.Type.ListType -> ListType(elementType = this.elementType.toType())
    is ast.Type.Variant ->
      VariantType(fields = this.fields.associate { it.label to it.type.toType() })

    is ast.Type.Ref -> RefType(inner.toType())
    is ast.Type.Bottom -> Bot
    is ast.Type.Top -> Top
  }

fun unreachable(): Nothing = error("unreachable")
