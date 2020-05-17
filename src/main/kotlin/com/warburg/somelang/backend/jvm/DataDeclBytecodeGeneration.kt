package com.warburg.somelang.backend.jvm

import com.warburg.somelang.ast.ValueConstructorDeclarationNode
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.middleend.getDeclarationFqn
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import kotlin.reflect.jvm.javaMethod

/**
 * @author ewarburg
 */
internal fun generateDataDecl(
    nodeFqnToClassWriter: Map<FullyQualifiedName, ClassWriter>,
    context: CompilationContext
) {
    val outerCw = context.cw
    val dd = context.currentDataDeclNode
    val outerFqn = dd.typeConstructorDeclaration.getDeclarationFqn()
    val outerInternalName = outerFqn.toJavaInternalName()

    // make outer class with data type's name
    // TODO Access modifiers, generic parms
    outerCw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT, outerInternalName, null, "java/lang/Object", null)
    generateConstructor(
        Opcodes.ACC_PROTECTED,
        Type.getType(Object::class.java),
        Method.getMethod(Object::class.java.getConstructor()),
        emptyList(),
        context
    )

    for (valueConstDecl in dd.valueConstructorDeclarations) {
        val innerSimpleName = valueConstDecl.nameNode.name.text
        val innerInternalName = valueConstDecl.getDeclarationFqn().toJavaInternalName()
        val innerAcc = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL
        outerCw.visitInnerClass(innerInternalName, outerInternalName, innerSimpleName, innerAcc)

        val innerCw = nodeFqnToClassWriter.getValue(valueConstDecl.getDeclarationFqn())
        context.withClassWriter(innerCw) {
            innerCw.visit(Opcodes.V1_8, innerAcc, innerInternalName, null, outerInternalName, null)
            generateFields(valueConstDecl, context)
            generateConstructor(
                Opcodes.ACC_PUBLIC,
                Type.getType(outerFqn.toObjectDescriptor().descriptor),
                Method.getMethod("void <init>()"),
                emptyList(),
                context
            )
            generateStandardDataMethods(
                valueConstDecl,
                innerInternalName,
                innerSimpleName,
                context
            )
            innerCw.visitEnd()
        }
    }
    outerCw.visitEnd()
}

private fun generateFields(valueConstDecl: ValueConstructorDeclarationNode, context: CompilationContext) {

}

private fun generateConstructor(access: Int, superClassType: Type, superConstructorMethod: Method, parameters: List<Any>, context: CompilationContext) {
    // TODO parameters in method
    val method = Method.getMethod("void <init>()")
    // TODO type parameters
    val mv = GeneratorAdapter(access, method, null, null, context.cw)
    context.withMethodVisitor(mv) {
        mv.loadThis()
        mv.invokeConstructor(superClassType, superConstructorMethod)
        // TODO assignments from parameters to fields
    }
    mv.returnValue()
    mv.finish()
}

private fun generateStandardDataMethods(
    valueConstDecl: ValueConstructorDeclarationNode,
    internalName: String,
    simpleName: String,
    context: CompilationContext
) {
    generateToString(simpleName, valueConstDecl, context)
    // TODO generate hashCode/equals
}

private fun generateToString(simpleName: String, valueConstDecl: ValueConstructorDeclarationNode, context: CompilationContext) {
    val mv = GeneratorAdapter(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
        Method.getMethod(Object::toString.javaMethod!!),
        null,
        null,
        context.cw
    )
    // TODO fields
    mv.visitLdcInsn(simpleName)
    mv.returnValue()
    mv.finish()
}