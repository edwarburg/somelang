package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name
import com.warburg.somelang.id.UnresolvedName
import com.warburg.somelang.id.resolve
import com.warburg.somelang.middleend.FunctionType
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

    val manifest = Manifest().apply {
        mainAttributes.putValue("Manifest-Version", "1.0")
        mainAttributes.putValue("Main-class", "com/warburg/Class0")
    }

    context.withCurrentOutputJarFile(jarFile) {
        JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
            compilationInput.filesToCompile
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
        nameNode = id(fqn("main"))
        parameters = emptyList()
        returnType = TypeNameNode(id("void"))
        body = NoOpNode
    }
    context.withCurrentDeclNode(mainDecl) {
        generateMethod(context) {
            /*
              int result = somelangMain()
              System.out.println(Integer.toString(result))
             */

            val mv = context.mv
            val somelangMainMethod = context.getMethod(context.input.idResolver.resolveId(UnresolvedName("somelangMain"))!!)!!
            val result = mv.newLocal(somelangMainMethod.returnType)
            mv.invokeStatic(context.getType(Descriptor("Lcom/warburg/Class0;")), somelangMainMethod)
            mv.storeLocal(result)
            mv.getStatic(context.getType(System::class.java), "out", context.getType(PrintStream::class.java))
            mv.loadLocal(result)
            if (somelangMainMethod.returnType.descriptor == "I") {
                mv.invokeStatic(
                    context.getType(Integer::class.java),
                    Method("toString", context.getType(String::class.java), arrayOf(Type.INT_TYPE))
                )
            }
            mv.invokeVirtual(context.getType(PrintStream::class.java), Method("println", Type.VOID_TYPE, arrayOf(context.getType(String::class.java))))
            // TODO is there a GeneratorAdaptor method for this?
            mv.returnValue()
        }
    }
}

private fun generateMethod(context: CompilationContext, bodyBuilder: () -> Unit) {
    val cw = context.cw
    val decl = context.currentDeclNode
    val methodFqn = decl.nameNode.name.resolve(context.input.idResolver) ?: throw IllegalArgumentException("Can't resolve name ${decl.nameNode.name}")
    val method = context.getMethod(methodFqn) ?: throw IllegalArgumentException("no method for ${decl.nameNode.name}")
    context.withMethodVisitor(GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, method, null, null, cw)) {
        val mv = context.mv
        context.symbolTable.withOpaqueFrame {
            try {
                val type = context.input.typeContext.lookupType(methodFqn) as? FunctionType ?: throw IllegalArgumentException("no type for decl ${decl.nameNode.name}")
                type.argumentTypes.forEachIndexed { i, arg ->
                    context.symbolTable.put(UnresolvedName(arg.name!!), LocalVarInfo(arg.name, i, arg.type.toASMType()))
                }
                bodyBuilder()
            } finally {
                // TODO compute a real max...
                mv.visitMaxs(1000, 1000)
                mv.endMethod()
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
            val typeOfLocal = node.type!!.toSomelangTime(context.input.idResolver).toASMType()
            val index = mv.newLocal(typeOfLocal)
            context.symbolTable.put(node.nameNode.name.asUnresolved(), LocalVarInfo(node.nameNode.name.text, index, typeOfLocal))
            generateNode(node.rhs, context)
            mv.storeLocal(index)
        }
        is InvokeNode -> {
            val targetDecl = context.getMethod(node.target.name.resolve(context.input.idResolver)!!)!!
            for (argNode in node.arguments.asReversed()) {
                when (argNode) {
                    is PositionalArgumentNode -> {
                        generateNode(argNode.value, context)
                    }
                }
            }
            context.mv.invokeStatic(context.getOwner(node.target.name.text), targetDecl)
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
fun FullyQualifiedName.asObjectDescriptor(): Descriptor = Descriptor("L${this.text.replace(".", "/")};")

private class CompilationContext(val input: CompilationInput) {
    var currentOutputJarFile: File by nullable()
    var currentFileNode: FileNode by nullable()
    var currentDeclNode: FunctionDeclarationNode by nullable()
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
    inline fun <A> withCurrentDeclNode(newValue: FunctionDeclarationNode, action: () -> A): A = withField(this::currentDeclNode, newValue) {
        withField(this::currFirstArg, 0) {
            withField(this::currFirstLocal, this.currentDeclNode.parameters.size + this.currFirstArg, action)
        }
    }
    inline fun <A> withClassWriter(newValue: ClassWriter, action: () -> A): A = withField(this::cw, newValue, action)
    inline fun <A> withMethodVisitor(newValue: GeneratorAdapter, action: () -> A): A = withField(this::mv, newValue, action)

    fun getType(clazz: KClass<*>): Type = getType(clazz.java)
    fun getType(clazz: Class<*>): Type = this.typeByClassCache.computeIfAbsent(clazz) { Type.getType(it) }
    fun getType(descriptor: Descriptor): Type = this.typeByDescriptorCache.computeIfAbsent(descriptor) { Type.getType(it.descriptor) }
    fun getMethod(target: FullyQualifiedName): Method? = if (target.text == "main") {
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
    fun getOwner(id: String): Type? = Type.getType("Lcom/warburg/Class0;")
}

private fun FunctionType.asMethod(name: FullyQualifiedName): Method =
    Method(name.text, this.returnType.toASMType(), this.argumentTypes.map { it.type.toASMType() }.toTypedArray())

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
