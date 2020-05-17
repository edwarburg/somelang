package com.warburg.somelang.backend.jvm

import com.warburg.somelang.backend.common.toASMType
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name
import com.warburg.somelang.id.UnresolvedName
import com.warburg.somelang.middleend.FunctionType
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * @author ewarburg
 */
fun FullyQualifiedName.toJavaInternalName(): String = if (this.isInnerClass) {
    "${this.qualifyingSegment.toJavaInternalName()}$${this.finalSegment}"
} else {
    this.text.replace(".", "/")
}

val FullyQualifiedName.isInnerClass: Boolean
    get() = this.finalSegment.firstOrNull()?.isUpperCase() == true && this.qualifyingSegment.finalSegment.firstOrNull()?.isUpperCase() == true

fun GeneratorAdapter.finish() {
    visitMaxs(0, 0)
    endMethod()
}

fun Name.asUnresolved(): UnresolvedName = when (this) {
    is UnresolvedName -> this
    else -> UnresolvedName(this.text)
}

/**
 * Newtype for JVM type descriptors, eg, `Ljava.lang.String;`
 */
inline class Descriptor(val descriptor: String)
fun FullyQualifiedName.toObjectDescriptor(): Descriptor =
    Descriptor("L${this.toJavaInternalName()};")

internal fun FunctionType.asMethod(name: FullyQualifiedName): Method =
    Method(name.finalSegment, this.returnType.toASMType(), this.argumentTypes.map { it.type.toASMType() }.toTypedArray())

