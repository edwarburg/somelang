package com.warburg.somelang.backend.jvm

import com.warburg.somelang.ast.DataDeclarationNode
import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.ast.FunctionDeclarationNode
import com.warburg.somelang.backend.common.CompilationInput
import com.warburg.somelang.common.NullableProperty
import com.warburg.somelang.common.SymbolTable
import com.warburg.somelang.common.nullable
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.UnresolvedName
import com.warburg.somelang.middleend.FunctionType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.jvm.isAccessible

/**
 * @author ewarburg
 */
internal class CompilationContext(val input: CompilationInput) {
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

data class LocalVarInfo(val name: String, val jvmLocalIdx: Int, val type: Type)