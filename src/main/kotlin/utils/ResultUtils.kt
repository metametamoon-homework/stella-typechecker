package utils

import com.github.michaelbull.result.BindingScope
import com.github.michaelbull.result.Err

fun <E> BindingScope<E>.raise(e: E): Nothing = Err(e).bind()
