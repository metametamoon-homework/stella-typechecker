package type.error

import ast.Application
import ast.Succ

data class SuccNotInt(override val errorNode: Succ) : TypeError

data class ApplicantNotOfFunctionType(override val errorNode: Application) : TypeError

data class BadArgumentType(override val errorNode: Application) : TypeError
