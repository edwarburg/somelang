package com.warburg.somelang.middleend

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.common.NameResolvingPhase
import com.warburg.somelang.common.TypecheckingPhase
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

fun typeAnalysis(node: FileNode<NameResolvingPhase>): FileNode<TypecheckingPhase> = node as FileNode<TypecheckingPhase>