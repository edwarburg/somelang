package com.warburg.somelang.backend.jvm

import com.warburg.somelang.ast.*
import com.warburg.somelang.backend.common.toASMType
import com.warburg.somelang.common.withOpaqueFrame
import com.warburg.somelang.id.UnresolvedName
import com.warburg.somelang.middleend.FunctionType
import com.warburg.somelang.middleend.getDeclarationFqn
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.PrintStream

/**
 * @author ewarburg
 */
internal fun generateMain(somelangMain: FunctionDeclarationNode, context: CompilationContext) {
    val mainDecl = functionDeclaration {
        nameNode = id(fqn("main"))
        parameters = emptyList()
        returnType = TypeNameNode(id("void"))
        body = NoOpNode
    }.withDeclFqn(context.currentFileNode.getDeclarationFqn() + "main")
    context.withCurrentFuncDeclNode(mainDecl) {
        generateFuncDecl(context) {
            /*
              int result = somelangMain()
              System.out.println(Integer.toString(result))
             */

            val mv = context.mv
            val somelangMainMethod = context.getMethod(somelangMain.getDeclarationFqn())!!
            val result = mv.newLocal(somelangMainMethod.returnType)
            mv.invokeStatic(
                context.getType(context.currentFileNode.getDeclarationFqn().toObjectDescriptor()),
                somelangMainMethod
            )
            mv.storeLocal(result)
            mv.getStatic(context.getType(System::class.java), "out", context.getType(PrintStream::class.java))
            mv.loadLocal(result)
            if (somelangMainMethod.returnType.descriptor == "I") {
                mv.invokeStatic(
                    context.getType(Integer::class.java),
                    Method("toString", context.getType(String::class.java), arrayOf(Type.INT_TYPE))
                )
            }
            mv.invokeVirtual(
                context.getType(PrintStream::class.java),
                Method("println", Type.VOID_TYPE, arrayOf(context.getType(Object::class.java)))
            )
            // TODO is there a GeneratorAdaptor method for this?
            mv.returnValue()
        }
    }
}

internal fun generateFuncDecl(context: CompilationContext, bodyBuilder: () -> Unit) {
    val cw = context.cw
    val decl = context.currentFuncDeclNode
    val methodFqn = decl.getDeclarationFqn()
    val method = context.getMethod(methodFqn) ?: throw IllegalArgumentException("no method for ${decl.nameNode.name}")
    context.withMethodVisitor(GeneratorAdapter(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, method, null, null, cw)) {
        val mv = context.mv
        context.symbolTable.withOpaqueFrame {
            try {
                val type = context.input.typeContext.lookupType(methodFqn) as? FunctionType ?: throw IllegalArgumentException("no type for decl ${decl.getDeclarationFqn()}")
                type.argumentTypes.forEachIndexed { i, arg ->
                    context.symbolTable.put(
                        UnresolvedName(arg.name!!),
                        LocalVarInfo(arg.name, i, arg.type.toASMType())
                    )
                }
                bodyBuilder()
            } finally {
                mv.finish()
            }
        }
    }
}