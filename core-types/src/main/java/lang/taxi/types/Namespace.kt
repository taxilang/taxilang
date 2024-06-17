package lang.taxi.types

/**
 * A namespace is (currently) a bit different from other sybmols,
 * as we don't treat it as a compiled "thing"
 *
 * This is for legacy reasons, rather than intentional design, and we can revisit.
 * However, when constructing a SymbolTree, we register Namespace items
 *
 * Named NamespaceToken to avoid clash with legacy Namespace typealias that's
 * littered all through compilation etc.
 */
class NamespaceToken(override val qualifiedName: String) : Named {
}
