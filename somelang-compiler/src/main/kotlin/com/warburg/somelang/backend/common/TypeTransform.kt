package com.warburg.somelang.backend.common

import com.warburg.somelang.ast.TypeExpressionNode
import com.warburg.somelang.ast.TypeNameNode
import com.warburg.somelang.backend.jvm.toObjectDescriptor
import com.warburg.somelang.common.NameResolvingPhase
import com.warburg.somelang.middleend.*
import org.objectweb.asm.Type

fun <P : NameResolvingPhase> TypeExpressionNode<P>.toSomelangType(): SomelangType = when (this) {
    is TypeNameNode<P> -> when (this.nameNode.name.text) {
        "Int" -> IntType
        "String" -> StringType
        "void" -> VoidType
        else -> SomelangObjectType(getDeclarationFqn())
    }
    else -> throw UnsupportedOperationException()
}

/**
 * @author ewarburg
 */
fun SomelangType.toASMType(): Type = when (this) {
    is IntType -> Type.INT_TYPE
    is VoidType -> Type.VOID_TYPE
    is SomelangObjectType -> Type.getType(this.fullyQualifiedName.toObjectDescriptor().text)
    // TODO Type for function?
    is FunctionType -> throw UnsupportedOperationException("Not supporting function values for the moment")
}