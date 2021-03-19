package lang.taxi.linter

import lang.taxi.CompilationMessage
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import java.nio.file.Path

interface LinterRule<T> {
   /**
    * Used by tooling to enable / disable certain rules, and to override severity etc.
    * Must be unique.
    * Follow the naming convention / style used by eslint:
    * https://eslint.org/docs/rules/
    */
   val id: String
   fun evaluate(source: T): List<CompilationMessage>
}


interface ModelLinterRule : LinterRule<ObjectType> {
}

interface EnumLinterRule : LinterRule<EnumType> {
}

interface TypeAliasLinterRule : LinterRule<TypeAlias> {
}

interface TypeLinterRule : LinterRule<Type> {
}

interface SourceFileLinterRule : LinterRule<Path> {
}
