package com.warburg.somelang.backend

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.ast.FunctionDeclarationNode
import com.warburg.somelang.ast.Node
import com.warburg.somelang.middleend.*
import me.tomassetti.kllvm.*


private typealias SomeLangInt = I32Type

fun convertToLLVM(files: List<FileNode>): String {
    val builder = ModuleBuilder()
    for (file in files) {
        addContents(file, builder)
    }
    return builder.IRCode().lines()
        // filter function body labels that llc doesn't seem to like...
        .filter { !"\\s+[a-z]+:$".toRegex().matches(it) }
        .joinToString("\n")
}

/**
 * @author ewarburg
 */
fun addContents(file: FileNode, builder: ModuleBuilder) {
    builder.addDeclaration(FunctionDeclaration("printf", I32Type, listOf(Pointer(I8Type)), varargs = true))
    for (node in file.nodes) {
        if (node is FunctionDeclarationNode) {
            addFunction(node, builder)
        }
    }
}

private fun addFunction(
    node: FunctionDeclarationNode,
    builder: ModuleBuilder
) {
    val function = if (node.nameNode.name.text == "main") {
        builder.createMainFunction()
    } else {
        builder.createFunction(node.nameNode.name.text, SomeLangInt, listOf())
    }

    addFunctionBody(node.body, function)
}

fun addFunctionBody(body: Node, function: FunctionBuilder) {
    val tac = body.asThreeAddressCode()
    tac.toFunction(function.createBlock("a"))
}

private fun TacBlock.toFunction(builder: BlockBuilder) {
    for (instruction in this.instructions) {
        when (instruction) {
            is DefineOp -> {
                val variable = builder.addVariable(SomeLangInt, instruction.defines.id)
                builder.assignVariable(variable, instruction.rhs.asLLVMValue(builder))
            }
            is BinOp -> {
                builder.addInstruction(TempValue(instruction.defines.id, instruction.asLLVMInstruction(builder)))
            }
            is ReturnOp -> {
                val value = instruction.value.asLLVMValue(builder)
                builder.addInstruction(Printf(builder.functionBuilder.stringConstForContent("Result: %d\n").reference(), value))
                builder.addInstruction(Return(value))
            }
        }
    }
}

private fun BinOp.asLLVMInstruction(builder: BlockBuilder): Instruction = when (this.op) {
    Op.IntAdd -> IntAddition(this.lhs.asLLVMValue(builder), this.rhs.asLLVMValue(builder))
}

private fun ValueReference.asLLVMValue(builder: BlockBuilder): Value  = when (this) {
    is Immediate<*> -> IntConst(this.value as Int, SomeLangInt)
    is TempVarReference -> LocalValueRef(this.variable.id, SomeLangInt)
    is LocalVarReference -> {
        val tempVar = TempValue("llvm${builder.functionBuilder.tmpIndex()}", Load(LocalValueRef(this.variable.id, Pointer(SomeLangInt))))
        builder.addInstruction(tempVar)
        LocalValueRef(tempVar.name, SomeLangInt)
    }
    is VariableReference -> TODO()
}
