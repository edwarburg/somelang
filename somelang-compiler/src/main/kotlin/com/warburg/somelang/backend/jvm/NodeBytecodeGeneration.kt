package com.warburg.somelang.backend.jvm

import com.warburg.somelang.ast.*
import com.warburg.somelang.backend.common.toASMType
import com.warburg.somelang.backend.common.toSomelangType
import com.warburg.somelang.common.withTransparentFrame
import com.warburg.somelang.middleend.getReferentFqn
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * @author ewarburg
 */
internal fun generateNode(node: Node<CodegenPrereqPhase>, context: CodegenContext) {
    val mv = context.mv

    when (node) {
        is NoOpNode -> {
            mv.visitInsn(Opcodes.NOP)
        }
        is IntLiteralNode<CodegenPrereqPhase> -> {
            mv.visitLdcInsn(node.value)
        }
        is StringLiteralNode<CodegenPrereqPhase> -> {
            mv.visitLdcInsn(node.value)
        }
        is ReadLocalVarNode<CodegenPrereqPhase> -> {
            val targetIdx = context.symbolTable.lookup(node.target.name.asUnresolved())?.jvmLocalIdx
                ?: throw UnsupportedOperationException("no local variable named ${node.target.name}")
            if (targetIdx < context.currFirstArg) {
                mv.loadThis()
            } else if (targetIdx >= context.currFirstArg && targetIdx < context.currFirstLocal) {
                mv.loadArg(targetIdx)
            } else {
                mv.loadLocal(targetIdx)
            }
        }
        is ReturnNode<CodegenPrereqPhase> -> {
            generateNode(node.value, context)
            mv.returnValue()
        }
        is BlockNode<CodegenPrereqPhase> -> {
            context.symbolTable.withTransparentFrame {
                for (statement in node.statements) {
                    generateNode(statement, context)
                }
            }
        }
        is BinaryOperationNode<CodegenPrereqPhase> -> {
            // TODO probably will support binary-operator-as-method-invocation type stuff here eventually
            val opcode = when (node.operator) {
                BinaryOperator.ADD -> GeneratorAdapter.ADD
            }
            generateNode(node.lhs, context)
            generateNode(node.rhs, context)
            // TODO actually look at types
            val type = Type.INT_TYPE
            mv.math(opcode, type)
        }
        is LocalVarDeclarationNode<CodegenPrereqPhase> -> {
            // TODO this type resolution should already be done at type analysis time
            val typeOfLocal = node.type!!.toSomelangType().toASMType()
            val index = mv.newLocal(typeOfLocal)
            context.symbolTable.put(node.nameNode.name.asUnresolved(),
                LocalVarInfo(node.nameNode.name.text, index, typeOfLocal)
            )
            generateNode(node.rhs, context)
            mv.storeLocal(index)
        }
        is InvokeNode<CodegenPrereqPhase> -> {
            val referentFqn = node.getReferentFqn()
            val targetDecl = context.getMethod(referentFqn)!!
            for (argNode in node.arguments.asReversed()) {
                when (argNode) {
                    is PositionalArgumentNode<CodegenPrereqPhase> -> {
                        generateNode(argNode.value, context)
                    }
                }
            }
            context.mv.invokeStatic(context.getOwner(referentFqn), targetDecl)
        }
        is ValueConstructorInvocationNode<CodegenPrereqPhase> -> {
            val referentFqn = node.getReferentFqn()
            val targetType = Type.getType(referentFqn.toObjectDescriptor().text)
            val isSingleton = true// TODO Determine cardinality of constructor
            if (isSingleton) {
                mv.getStatic(targetType, SINGLETON_FIELD_NAME, targetType)
            } else {
                mv.newInstance(targetType)
                mv.dup()
                // TODO parameters
                mv.invokeConstructor(targetType, Method.getMethod("void <init>()"))
            }
        }
        else -> throw UnsupportedOperationException("Don't know how to compile $node")
    }
}
