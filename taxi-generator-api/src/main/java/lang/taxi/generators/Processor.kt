package lang.taxi.generators

import lang.taxi.Type
import lang.taxi.types.Field

interface Processor {

}

interface FieldProcessor<KBuilderType> : Processor {
    fun process(builder: KBuilderType, field: Field): KBuilderType
}

interface TypeProcessor<KBuilderType> : Processor {
    fun process(builder: KBuilderType, type: Type): KBuilderType
}