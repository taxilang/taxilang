package lang.taxi.compiler

import lang.taxi.types.DefinableToken

/**
 * Thrown / Created when we hit a circular reference whilst compiling code
 * and cannot resolve it.
 *
 * Note: Generally, we try to avoid throwing exceptions in the compiler,
 * so this may be attached to a CompilationError without being thrown.
 *
 */
class CircularReferenceException(val token: DefinableToken<*>) : RuntimeException("A circular reference was encountered compiling ${token.qualifiedName}") {
}
