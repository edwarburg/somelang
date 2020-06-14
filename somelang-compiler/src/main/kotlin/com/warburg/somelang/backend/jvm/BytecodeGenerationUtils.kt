package com.warburg.somelang.backend.jvm

import com.warburg.somelang.backend.common.toASMType
import com.warburg.somelang.common.TypecheckingPhase
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name
import com.warburg.somelang.id.UnresolvedName
import com.warburg.somelang.middleend.FunctionType
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

typealias CodegenPrereqPhase = TypecheckingPhase

/**
 * @author ewarburg
 */
fun FullyQualifiedName.toJavaInternalName(): JavaInternalName = JavaInternalName(
    if (this.isInnerClass) {
        "${this.qualifyingSegment.toJavaInternalName().text}$${this.finalSegment}"
    } else {
        this.text.replace(".", "/")
    }
)

inline class JavaInternalName(val text: String)

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
inline class Descriptor(val text: String)
fun JavaInternalName.toObjectDescriptor(): Descriptor = Descriptor("L${this.text};")
fun FullyQualifiedName.toObjectDescriptor(): Descriptor = toJavaInternalName().toObjectDescriptor()

internal fun FunctionType.asMethod(name: FullyQualifiedName): Method =
    Method(name.finalSegment, this.returnType.toASMType(), this.argumentTypes.map { it.type.toASMType() }.toTypedArray())

const val SINGLETON_FIELD_NAME: String = "INSTANCE"