package ast

@Suppress("TooManyFunctions")
interface AstVisitor<in Ctx, out T> {
  fun visitSucc(succ: Succ, ctx: Ctx): T

  fun visitVar(varExpr: Var, ctx: Ctx): T

  fun visitApplication(application: Application, ctx: Ctx): T

  fun visitProgram(program: Program, ctx: Ctx): T

  fun visitFunctionDeclaration(functionDeclaration: FunctionDeclaration, ctx: Ctx): T

  fun visitParamDeclaration(paramDeclaration: ParamDeclaration, ctx: Ctx): T

  fun visitFunParamDeclaration(funDeclaration: FunDeclaration, ctx: Ctx): T

  fun visitTypeBool(typeBool: Type.Bool, ctx: Ctx): T

  fun visitTypeNat(typeNat: Type.Nat, ctx: Ctx): T

  fun visitTypeFun(typeFun: Type.Fun, ctx: Ctx): T

  fun visitTrueLiteral(trueLiteral: TrueLiteral, ctx: Ctx): T

  fun visitFalseLiteral(falseLiteral: FalseLiteral, ctx: Ctx): T

  fun visitIfExpr(expression: IfExpression, ctx: Ctx): T

  fun visitIsZero(isZero: IsZero, ctx: Ctx): T
}
