package com.warburg.somelang.backend

import com.warburg.somelang.ast.TypeExpressionNode
import com.warburg.somelang.ast.TypeNameNode
import com.warburg.somelang.id.resolve
import com.warburg.somelang.middleend.*
import org.objectweb.asm.Type

fun TypeExpressionNode.toSomelangTime(idResolver: IdResolver): SomelangType = when (this) {
    is TypeNameNode -> when (this.nameNode.name.text) {
        "Int" -> IntType
        "String" -> StringType
        "void" -> VoidType
        else -> SomelangObjectType(this.nameNode.name.resolve(idResolver)!!)
    }
    else -> throw UnsupportedOperationException()
}

/**
 * @author ewarburg
 */
fun SomelangType.toASMType(): Type = when (this) {
    is IntType -> Type.INT_TYPE
    is VoidType -> Type.VOID_TYPE
    is SomelangObjectType -> Type.getType(this.fullyQualifiedName.asObjectDescriptor().descriptor)
    // TODO Type for function?
    is FunctionType -> throw UnsupportedOperationException("Not supporting function values for the moment")
}