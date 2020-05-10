package com.warburg.somelang.middleend

import com.warburg.somelang.id.FullyQualifiedName

/**
 * @author ewarburg
 */
sealed class SomelangType
interface PrimitiveType
interface ObjectType {
    val fullyQualifiedName: FullyQualifiedName
}

open class SomelangObjectType(override val fullyQualifiedName: FullyQualifiedName): SomelangType(), ObjectType

object IntType : SomelangType(), PrimitiveType
object StringType : SomelangObjectType(FullyQualifiedName("java.lang.String"))
object VoidType : SomelangType()
data class FunctionType(val argumentTypes: List<ArgumentType>, val returnType: SomelangType) : SomelangType()
data class ArgumentType(val name: String?, val type: SomelangType)
