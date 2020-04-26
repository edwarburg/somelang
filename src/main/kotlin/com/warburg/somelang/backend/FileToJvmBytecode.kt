package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.*
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.isAccessible

/**
 * @author ewarburg
 */
fun convertToJvmBytecode(files: List<FileNode>, outPath: Path) {
    val context = CompilationContext()
    val jarFile = outPath.toFile()
    jarFile.createNewFile()
    context.currentOutputJarFile = jarFile

    // TODO main with runtime parameters?
    // TODO fix hard coding of class names and types etc
    // TODO actually use types in compilation instead of assuming everything is an int
    // TODO invokes of (somelang) functions and (java) methods

    val manifest = Manifest().apply {
        mainAttributes.putValue("Manifest-Version", "1.0")
        mainAttributes.putValue("Main-class", "com/warburg/Class0")
    }

    context.withCurrentOutputJarFile(jarFile) {
        JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
            files
                .forEachIndexed { i, fileNode ->
                    context.withCurrentFileNode(fileNode) {
                        val classBytes = generateClassForFile(i, context)
                        jos.putNextEntry(ZipEntry("com/warburg/Class0.class"))
                        jos.write(classBytes)
                        jos.closeEntry()
                    }
                }
        }
    }
}

private fun generateClassForFile(i: Int, context: CompilationContext): ByteArray {
    context.withClassWriter(ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)) {
        context.cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/warburg/Class$i", null, "java/lang/Object", null)
        for (node in context.currentFileNode.nodes) {
            when (node) {
                is FunctionDeclarationNode -> {
                    context.withCurrentDeclNode(node) {
                        generateMethod(context) {
                            generateNode(node.body, context)
                        }
                    }
                }
                else -> {
                    throw UnsupportedOperationException("Don't know how to compile node to JVM from top level: $node")
                }
            }
        }
        generateMain(context)
        return context.cw.toByteArray()
    }
}

private fun generateMain(context: CompilationContext) {
    val mainDecl = functionDeclaration {
        name = IdentifierNode("main")
        body = NoOpNode
    }
    context.withCurrentDeclNode(mainDecl) {
        generateMethod(context) {
            /*
              int result = somelangMain()
              System.out.println(Integer.toString(result))
             */

            val mv = context.mv
            val result = mv.newLocal(Type.INT_TYPE)
            mv.invokeStatic(context.getType("Lcom/warburg/Class0;"), Method("somelangMain", Type.INT_TYPE, emptyArray()))
            mv.storeLocal(result)
            mv.getStatic(context.getType(System::class.java), "out", context.getType(PrintStream::class.java))
            mv.loadLocal(result)
            mv.invokeStatic(context.getType(Integer::class.java), Method("toString", context.getType(String::class.java), arrayOf(Type.INT_TYPE)))
            mv.invokeVirtual(context.getType(PrintStream::class.java), Method("println", Type.VOID_TYPE, arrayOf(context.getType(String::class.java))))
            // TODO is there a GeneratorAdaptor method for this?
            mv.visitInsn(Opcodes.RETURN)
        }
    }
}

private fun generateMethod(context: CompilationContext, bodyBuilder: () -> Unit) {
    val cw = context.cw
    val decl = context.currentDeclNode
    val method = decl.asMethod()
    context.withMethodVisitor(GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, method, null, null, cw)) {
        val mv = context.mv
        context.symbolTable.withOpaqueFrame {
            try {
                bodyBuilder()
            } finally {
                // TODO compute a real max...
                mv.visitMaxs(1000, 1000)
                mv.endMethod()
            }
        }
    }
}

// TODO once we get to real types, constructing this as a `Type` may be easier than calling `descriptor()`
private fun FunctionDeclarationNode.asMethod(): Method = Method(this.name.id, descriptor())

private fun generateNode(node: Node, context: CompilationContext) {
    val mv = context.mv

    when (node) {
        is NoOpNode -> {
            mv.visitInsn(Opcodes.NOP)
        }
        is IntLiteralNode -> {
            mv.visitLdcInsn(node.value)
        }
        is ReadLocalVarNode -> {
            val targetIdx = context.symbolTable.lookup(node.target.id)?.jvmLocalIdx
                ?: throw UnsupportedOperationException("no local variable named ${node.target.id}")
            mv.loadLocal(targetIdx)
        }
        is ReturnNode -> {
            generateNode(node.value, context)
            val opcode = when {
                // TODO actually look at return type of the current func
                context.currentDeclNode.name.id == "main" -> Opcodes.RETURN
                else -> Opcodes.IRETURN
            }
            mv.visitInsn(opcode)
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
            // TODO get real type from var decl
            val index = mv.newLocal(Type.INT_TYPE)
            context.symbolTable.put(node.name.id, LocalVarInfo(node.name.id, index))
            generateNode(node.rhs, context)
            mv.storeLocal(index)
        }
        else -> throw UnsupportedOperationException("Don't know how to compile $node")
    }
}

private fun FunctionDeclarationNode.descriptor(): String = when {
    name.id == "main" -> "([Ljava/lang/String;)V"
    else -> "()I"
}

private fun FunctionDeclarationNode.signature(): String? = null


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

private class CompilationContext {
    var currentOutputJarFile: File by nullable()
    var currentFileNode: FileNode by nullable()
    var currentDeclNode: FunctionDeclarationNode by nullable()
    var cw: ClassWriter by nullable()
    var mv: GeneratorAdapter by nullable()

    val symbolTable: SymbolTable<LocalVarInfo> = SymbolTable()

    private val typeByClassCache: MutableMap<Class<*>, Type> = mutableMapOf()
    private val typeByDescriptorCache: MutableMap<String, Type> = mutableMapOf()

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
    inline fun <A> withCurrentDeclNode(newValue: FunctionDeclarationNode, action: () -> A): A = withField(this::currentDeclNode, newValue, action)
    inline fun <A> withClassWriter(newValue: ClassWriter, action: () -> A): A = withField(this::cw, newValue, action)
    inline fun <A> withMethodVisitor(newValue: GeneratorAdapter, action: () -> A): A = withField(this::mv, newValue, action)

    fun getType(clazz: KClass<*>): Type = getType(clazz.java)
    fun getType(clazz: Class<*>): Type = this.typeByClassCache.computeIfAbsent(clazz) { Type.getType(it) }
    fun getType(descriptor: String): Type = this.typeByDescriptorCache.computeIfAbsent(descriptor) { Type.getType(it) }
}

data class LocalVarInfo(val name: String, val jvmLocalIdx: Int)

private typealias Frame<A> = MutableMap<String, A>

class SymbolTable<A> {
    private val stack: MutableList<Frame<A>> = ArrayList()
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

    fun lookup(key: String): A? {
        if (this.stack.isEmpty()) {
            return null
        }

        var currentFrameIdx = this.stack.size - 1
        var currentFrame: Frame<A>
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

    fun put(key: String, value: A) {
        this.stack.last()[key] = value
    }

    inline fun lookupOrPut(key: String, valueSupplier: () -> A): A = lookup(key) ?: run {
        val value = valueSupplier()
        put(key, value)
        return value
    }
}

inline fun <A> SymbolTable<*>.withFrame(opaque: Boolean, action: () -> A): A {
    try {
        pushFrame(opaque)
        return action()
    } finally {
        popFrame()
    }
}

inline fun <A> SymbolTable<*>.withOpaqueFrame(action: () -> A): A = withFrame(true, action)
inline fun <A> SymbolTable<*>.withTransparentFrame(action: () -> A): A = withFrame(false, action)
