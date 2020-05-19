package com.warburg.somelang.compiler

import com.warburg.somelang.backend.common.CodegenInput
import com.warburg.somelang.backend.jvm.convertToJvmBytecode
import com.warburg.somelang.frontend.parse
import com.warburg.somelang.middleend.TypeContext
import com.warburg.somelang.middleend.resolveNames
import com.warburg.somelang.middleend.typeAnalysis
import java.nio.file.Path

/**
 * @author ewarburg
 */
class Compiler(
    private val inDir: Path,
    private val outDir: Path
) {
    fun compile() {
        val files = this.inDir.toAbsolutePath().toFile().listFiles()

        val parsedFiles = files!!.asSequence()
            .filter { it.name.endsWith(".som") }
            .map { it.readText().parse() }
            .toList()

        val nameResolved = parsedFiles.map { resolveNames(it) }
        val typeAnalyzed = nameResolved.map { typeAnalysis(it) }
//        convertToJvmBytecode(CodegenInput(nameResolved, TypeContext(), this.outDir)) // illegal, wrong phase
        convertToJvmBytecode(CodegenInput(typeAnalyzed, TypeContext(), this.outDir))
    }

    companion object {
    }
}