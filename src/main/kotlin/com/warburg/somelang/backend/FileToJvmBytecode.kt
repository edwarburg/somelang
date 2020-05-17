package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name
import com.warburg.somelang.id.UnresolvedName
import com.warburg.somelang.middleend.FunctionType
import com.warburg.somelang.middleend.getDeclarationFqn
import com.warburg.somelang.middleend.getReferentFqn
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod

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
        OutputClass(cw.toByteArray(), ZipEntry(ownerFqn.toJavaInternalName() + ".class"))
    }
}

private fun makeAndInitClassWriter(javaClassName: String): ClassWriter {
    val cw = ClassWriter(CLASSWRITER_OPTIONS)
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, javaClassName, null, "java/lang/Object", null)
    return cw
}

fun FullyQualifiedName.toJavaInternalName(): String = if (this.isInnerClass) {
    "${this.qualifyingSegment.toJavaInternalName()}$${this.finalSegment}"
} else {
    this.text.replace(".", "/")
}

private val FullyQualifiedName.isInnerClass: Boolean
    get() = this.finalSegment.firstOrNull()?.isUpperCase() == true && this.qualifyingSegment.finalSegment.firstOrNull()?.isUpperCase() == true

private fun generateDataDecl(
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
    generateConstructor(Opcodes.ACC_PROTECTED, Type.getType(Object::class.java), Method.getMethod(Object::class.java.getConstructor()), emptyList(), context)

    for (valueConstDecl in dd.valueConstructorDeclarations) {
        val innerSimpleName = valueConstDecl.nameNode.name.text
        val innerInternalName = valueConstDecl.getDeclarationFqn().toJavaInternalName()
        val innerAcc = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL
        outerCw.visitInnerClass(innerInternalName, outerInternalName, innerSimpleName, innerAcc)

        val innerCw = nodeFqnToClassWriter.getValue(valueConstDecl.getDeclarationFqn())
        context.withClassWriter(innerCw) {
            innerCw.visit(Opcodes.V1_8, innerAcc, innerInternalName, null, outerInternalName, null)
            generateFields(valueConstDecl, context)
            generateConstructor(Opcodes.ACC_PUBLIC, Type.getType(outerFqn.toObjectDescriptor().descriptor), Method.getMethod("void <init>()"), emptyList(), context)
            generateStandardDataMethods(valueConstDecl, innerInternalName, innerSimpleName, context)
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
    val mv = GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, Method.getMethod(Object::toString.javaMethod!!), null, null, context.cw)
    // TODO fields
    mv.visitLdcInsn(simpleName)
    mv.returnValue()
    mv.finish()
}

fun GeneratorAdapter.finish() {
    visitMaxs(0, 0)
    endMethod()
}

private fun generateMain(somelangMain: FunctionDeclarationNode, context: CompilationContext) {
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
            mv.invokeStatic(context.getType(Descriptor("L${context.currentFileNode.getDeclarationFqn().toJavaInternalName()};")), somelangMainMethod)
            mv.storeLocal(result)
            mv.getStatic(context.getType(System::class.java), "out", context.getType(PrintStream::class.java))
            mv.loadLocal(result)
            if (somelangMainMethod.returnType.descriptor == "I") {
                mv.invokeStatic(
                    context.getType(Integer::class.java),
                    Method("toString", context.getType(String::class.java), arrayOf(Type.INT_TYPE))
                )
            }
            mv.invokeVirtual(context.getType(PrintStream::class.java), Method("println", Type.VOID_TYPE, arrayOf(context.getType(Object::class.java))))
            // TODO is there a GeneratorAdaptor method for this?
            mv.returnValue()
        }
    }
}

private fun generateFuncDecl(context: CompilationContext, bodyBuilder: () -> Unit) {
    val cw = context.cw
    val decl = context.currentFuncDeclNode
    val methodFqn = decl.getDeclarationFqn()
    val method = context.getMethod(methodFqn) ?: throw IllegalArgumentException("no method for ${decl.nameNode.name}")
    context.withMethodVisitor(GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, method, null, null, cw)) {
        val mv = context.mv
        context.symbolTable.withOpaqueFrame {
            try {
                val type = context.input.typeContext.lookupType(methodFqn) as? FunctionType ?: throw IllegalArgumentException("no type for decl ${decl.getDeclarationFqn()}")
                type.argumentTypes.forEachIndexed { i, arg ->
                    context.symbolTable.put(UnresolvedName(arg.name!!), LocalVarInfo(arg.name, i, arg.type.toASMType()))
                }
                bodyBuilder()
            } finally {
                mv.finish()
            }
        }
    }
}

private fun generateNode(node: Node, context: CompilationContext) {
    val mv = context.mv

    when (node) {
        is NoOpNode -> {
            mv.visitInsn(Opcodes.NOP)
        }
        is IntLiteralNode -> {
            mv.visitLdcInsn(node.value)
        }
        is StringLiteralNode -> {
            mv.visitLdcInsn(node.value)
        }
        is ReadLocalVarNode -> {
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
        is ReturnNode -> {
            generateNode(node.value, context)
            mv.returnValue()
        }
        is BlockNode -> {
            context.symbolTable.withTransparentFrame {
                for (statement in node.statements) {
                    generateNode(statement, context)
                }
            }
        }
        is BinaryOperationNode -> {
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
        is LocalVarDeclarationNode -> {
            // TODO this type resolution should already be done at type analysis time
            val typeOfLocal = node.type!!.toSomelangType().toASMType()
            val index = mv.newLocal(typeOfLocal)
            context.symbolTable.put(node.nameNode.name.asUnresolved(), LocalVarInfo(node.nameNode.name.text, index, typeOfLocal))
            generateNode(node.rhs, context)
            mv.storeLocal(index)
        }
        is InvokeNode -> {
            val referentFqn = node.getReferentFqn()
            val targetDecl = context.getMethod(referentFqn)!!
            for (argNode in node.arguments.asReversed()) {
                when (argNode) {
                    is PositionalArgumentNode -> {
                        generateNode(argNode.value, context)
                    }
                }
            }
            context.mv.invokeStatic(context.getOwner(referentFqn), targetDecl)
        }
        is ValueConstructorInvocationNode -> {
            val referentFqn = node.getReferentFqn()
            val mv = context.mv
            val targetType = Type.getType(referentFqn.toObjectDescriptor().descriptor)
            mv.newInstance(targetType)
            mv.dup()
            // TODO parameters
            mv.invokeConstructor(targetType, Method.getMethod("void <init>()"))
        }
        else -> throw UnsupportedOperationException("Don't know how to compile $node")
    }
}

fun Name.asUnresolved(): UnresolvedName = when (this) {
    is UnresolvedName -> this
    else -> UnresolvedName(this.text)
}

fun <A> nullable(): NullableProperty<A> = NullableProperty()

class NullableProperty<A> {
    private var theVal: A? = null

    val hasValue: Boolean
        get() = this.theVal != null

    fun clearValue() {
        this.theVal = null
    }

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): A = this.theVal ?: throw KotlinNullPointerException("Property ${prop.name} was read but hasn't been set (or was cleared)")

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: A) {
        this.theVal = value
    }
}

/**
 * Newtype for JVM type descriptors, eg, `Ljava.lang.String;`
 */
inline class Descriptor(val descriptor: String)
fun FullyQualifiedName.toObjectDescriptor(): Descriptor = Descriptor("L${this.toJavaInternalName()};")

private class CompilationContext(val input: CompilationInput) {
    var currentOutputJarFile: File by nullable()
    var currentFileNode: FileNode by nullable()
    var currentDataDeclNode: DataDeclarationNode by nullable()
    var currentFuncDeclNode: FunctionDeclarationNode by nullable()
    var currFirstArg: Int by nullable()
    var currFirstLocal: Int by nullable()
    var cw: ClassWriter by nullable()
    var mv: GeneratorAdapter by nullable()

    val symbolTable: SymbolTable<UnresolvedName, LocalVarInfo> = SymbolTable()

    private val typeByClassCache: MutableMap<Class<*>, Type> = mutableMapOf()
    private val typeByDescriptorCache: MutableMap<Descriptor, Type> = mutableMapOf()
    private val methodsForFuncs: MutableMap<FullyQualifiedName, Method> = mutableMapOf()

    inline fun <Result, Field> withField(property: KMutableProperty0<Field>, newValue: Field, action: () -> Result): Result {
        val delegate = property.getDelegateAs<NullableProperty<Field>>()
        val old: Field? = if (delegate.hasValue) property.get() else null
        property.set(newValue)
        try {
            return action()
        } finally {
            if (old != null) {
                property.set(old)
            } else {
                delegate.clearValue()
            }
        }
    }

    inline fun <reified R> KMutableProperty0<*>.getDelegateAs(): R {
        isAccessible = true
        val delegate = getDelegate()
        return delegate as? R ?: throw UnsupportedOperationException("Trying to get delegate of $this as ${R::class.java.simpleName} but it's ${if (delegate == null) "null" else "an instance of ${delegate::class.java.simpleName}"}")
    }

    inline fun <A> withCurrentOutputJarFile(newValue: File, action: () -> A): A = withField(this::currentOutputJarFile, newValue, action)
    inline fun <A> withCurrentFileNode(newValue: FileNode, action: () -> A): A = withField(this::currentFileNode, newValue, action)
    inline fun <A> withCurrentDataDeclNode(newValue: DataDeclarationNode, action: () -> A): A = withField(this::currentDataDeclNode, newValue, action)
    inline fun <A> withCurrentFuncDeclNode(newValue: FunctionDeclarationNode, action: () -> A): A = withField(this::currentFuncDeclNode, newValue) {
        withField(this::currFirstArg, 0) {
            withField(this::currFirstLocal, this.currentFuncDeclNode.parameters.size + this.currFirstArg, action)
        }
    }
    inline fun <A> withClassWriter(newValue: ClassWriter, action: () -> A): A = withField(this::cw, newValue, action)
    inline fun <A> withMethodVisitor(newValue: GeneratorAdapter, action: () -> A): A = withField(this::mv, newValue, action)

    fun getType(clazz: KClass<*>): Type = getType(clazz.java)
    fun getType(clazz: Class<*>): Type = this.typeByClassCache.computeIfAbsent(clazz) { Type.getType(it) }
    fun getType(descriptor: Descriptor): Type = this.typeByDescriptorCache.computeIfAbsent(descriptor) { Type.getType(it.descriptor) }
    fun getMethod(target: FullyQualifiedName): Method? = if (target.finalSegment == "main") {
        Method.getMethod("void main(String[])")
    } else {
        this.methodsForFuncs[target]?.let { return it }
        val typeOfTarget = this.input.typeContext.lookupType(target)
        if (typeOfTarget !is FunctionType) {
            throw IllegalArgumentException("trying to use $target as a function/method but it's a $typeOfTarget")
        }
        val method = typeOfTarget.asMethod(target)
        this.methodsForFuncs[target] = method
        method
    }
    fun getOwner(fqn: FullyQualifiedName): Type? = getType(fqn.qualifyingSegment.toObjectDescriptor())
}

private fun FunctionType.asMethod(name: FullyQualifiedName): Method =
    Method(name.finalSegment, this.returnType.toASMType(), this.argumentTypes.map { it.type.toASMType() }.toTypedArray())

data class LocalVarInfo(val name: String, val jvmLocalIdx: Int, val type: Type)

private typealias Frame<Key, Value> = MutableMap<Key, Value>

class SymbolTable<Key, Value> {
    private val stack: MutableList<Frame<Key, Value>> = ArrayList()
    private val opaque: MutableList<Boolean> = ArrayList()

    fun pushOpaqueFrame() {
        pushFrame(true)
    }

    fun pushTransparentFrame() {
        pushFrame(false)
    }

    fun pushFrame(opaque: Boolean) {
        this.stack.add(mutableMapOf())
        this.opaque.add(opaque)
    }

    fun popFrame() {
        val lastIndex = this.stack.size - 1
        this.stack.removeAt(lastIndex)
        this.opaque.removeAt(lastIndex)
    }

    fun lookup(key: Key): Value? {
        if (this.stack.isEmpty()) {
            return null
        }

        var currentFrameIdx = this.stack.size - 1
        var currentFrame: Frame<Key, Value>
        do {
            currentFrame = this.stack[currentFrameIdx]
            val value = currentFrame[key]
            if (value != null) {
                return value
            }
            currentFrameIdx--
        } while (currentFrameIdx >= 0 && !this.opaque[currentFrameIdx])

        return null
    }

    fun put(key: Key, value: Value) {
        this.stack.last()[key] = value
    }

    inline fun lookupOrPut(key: Key, valueSupplier: () -> Value): Value = lookup(key) ?: run {
        val value = valueSupplier()
        put(key, value)
        return value
    }
}

inline fun <A> SymbolTable<*, *>.withFrame(opaque: Boolean, action: () -> A): A {
    try {
        pushFrame(opaque)
        return action()
    } finally {
        popFrame()
    }
}

inline fun <A> SymbolTable<*, *>.withOpaqueFrame(action: () -> A): A = withFrame(true, action)
inline fun <A> SymbolTable<*, *>.withTransparentFrame(action: () -> A): A = withFrame(false, action)
