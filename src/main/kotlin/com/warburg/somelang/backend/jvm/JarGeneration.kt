package com.warburg.somelang.backend.jvm

import com.warburg.somelang.ast.DataDeclarationNode
import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.ast.FunctionDeclarationNode
import com.warburg.somelang.backend.common.CompilationInput
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.middleend.getDeclarationFqn
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.FileOutputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

/**
 * @author ewarburg
 */
fun convertToJvmBytecode(compilationInput: CompilationInput) {
    val context = CompilationContext(compilationInput)
    val jarFile = compilationInput.outPath.toFile()
    jarFile.createNewFile()
    context.currentOutputJarFile = jarFile

    // TODO main with runtime parameters?
    // TODO fix hard coding of class names and types etc

    val fileWithMain = findMain(compilationInput)

    val manifest = Manifest().apply {
        mainAttributes.putValue("Manifest-Version", "1.0")
        if (fileWithMain != null) {
            mainAttributes.putValue("Main-class", fileWithMain.getDeclarationFqn().toJavaInternalName())
        }
    }

    context.withCurrentOutputJarFile(jarFile) {
        JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
            compilationInput.filesToCompile
                .flatMap { fileNode ->
                    context.withCurrentFileNode(fileNode) {
                        generateClassesForFile(context)
                    }
                }.forEach {
                    jos.putNextEntry(it.zipEntry)
                    jos.write(it.bytes)
                    jos.closeEntry()
                }
        }
    }
}

const val MAIN_FUNC_NAME: String = "somelangMain"

fun findMain(compilationInput: CompilationInput): FileNode? {
    return compilationInput.filesToCompile.filter { fileNode ->
        fileNode.nodes.any { topLevelNode -> topLevelNode is FunctionDeclarationNode && topLevelNode.nameNode.name.text == MAIN_FUNC_NAME }
    }.also { nodes ->
        if (nodes.size > 1) {
            throw UnsupportedOperationException("More than one main")
        }
    }.firstOrNull()
}

class OutputClass(val bytes: ByteArray, val zipEntry: ZipEntry)

private const val CLASSWRITER_OPTIONS = ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES

private fun generateClassesForFile(context: CompilationContext): Collection<OutputClass> {
    val nodeFqnToClassWriter = mutableMapOf<FullyQualifiedName, ClassWriter>()
    val classWriterToOwnerFqn = mutableMapOf<ClassWriter, FullyQualifiedName>()

    val dataDecls = mutableListOf<DataDeclarationNode>()
    val funcDecls = mutableListOf<FunctionDeclarationNode>()

    val fileFqn = context.currentFileNode.getDeclarationFqn()
    val fileCw = makeAndInitClassWriter(fileFqn.toJavaInternalName())
    nodeFqnToClassWriter[fileFqn] = fileCw
    classWriterToOwnerFqn[fileCw] = fileFqn

    // map nodes to the class writers they go in

    for (node in context.currentFileNode.nodes) {
        when (node) {
            is DataDeclarationNode -> {
                // new class for data decl
                dataDecls.add(node)
                val typeConstFqn = node.typeConstructorDeclaration.getDeclarationFqn()
                val typeConstCw = ClassWriter(CLASSWRITER_OPTIONS)
                nodeFqnToClassWriter[typeConstFqn] = typeConstCw
                classWriterToOwnerFqn[typeConstCw] = typeConstFqn
                for (valueConstDecl in node.valueConstructorDeclarations) {
                    val valueConstFqn = valueConstDecl.getDeclarationFqn()
                    val valConstCw = ClassWriter(CLASSWRITER_OPTIONS)
                    nodeFqnToClassWriter[valueConstFqn] = valConstCw
                    classWriterToOwnerFqn[valConstCw] = valueConstFqn
                }
            }
            is FunctionDeclarationNode -> {
                // add decl to class for file
                funcDecls.add(node)
                // TODO add funcs to class of associated data?
                nodeFqnToClassWriter[node.getDeclarationFqn()] = fileCw
            }
            else -> throw UnsupportedOperationException("Don't know how to compile $node")
        }
    }

    // for each node, generate appropriate bytecode in the right class writer

    for (dataDecl in dataDecls) {
        val cw = nodeFqnToClassWriter[dataDecl.getDeclarationFqn()]!!
        context.withClassWriter(cw) {
            context.withCurrentDataDeclNode(dataDecl) {
                generateDataDecl(nodeFqnToClassWriter, context)
            }
        }
    }

    for (funcDecl in funcDecls) {
        val cw = nodeFqnToClassWriter[funcDecl.getDeclarationFqn()]!!
        context.withClassWriter(cw) {
            context.withCurrentFuncDeclNode(funcDecl) {
                generateFuncDecl(context) {
                    generateNode(funcDecl.body, context)
                }
            }
            if (funcDecl.nameNode.name.text == MAIN_FUNC_NAME) {
                generateMain(funcDecl, context)
            }
        }
    }

    // build the class writers into .class byte arrays paired with zip entries they'll go to
    return classWriterToOwnerFqn.map { (cw, ownerFqn) ->
        cw.visitEnd()
        OutputClass(
            cw.toByteArray(),
            ZipEntry(ownerFqn.toJavaInternalName() + ".class")
        )
    }
}

private fun makeAndInitClassWriter(javaClassName: String): ClassWriter {
    val cw = ClassWriter(CLASSWRITER_OPTIONS)
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, javaClassName, null, "java/lang/Object", null)
    return cw
}

