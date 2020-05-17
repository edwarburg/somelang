package com.warburg.somelang.middleend

import com.warburg.somelang.id.FullyQualifiedName

/**
 * @author ewarburg
 */
class TypeContext {
    private val types: MutableMap<FullyQualifiedName, SomelangType> = mutableMapOf()
    private val main: SomelangType = FunctionType(emptyList(), VoidType)

    fun lookupType(name: FullyQualifiedName): SomelangType? = if (name.finalSegment == "main") this.main else this.types[name]
    fun putType(name: FullyQualifiedName, type: SomelangType) {
        this.types[name] = type
    }
}
