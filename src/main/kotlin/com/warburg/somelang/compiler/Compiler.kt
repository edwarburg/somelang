package com.warburg.somelang.compiler

import com.warburg.somelang.backend.convertToLLVM
import com.warburg.somelang.frontend.parse
import com.warburg.somelang.runCommand
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

        // parse them
        val parsedFiles = files.asSequence()
            .filter { it.name.endsWith(".som") }
            .map { it.readText().parse() }
            .toList()

        // convert to an LLVM module string
        val llvmifiedFiles = convertToLLVM(parsedFiles)
        println(llvmifiedFiles)

        // write out
        val outf = Path.of(this.outDir.toAbsolutePath().toString(), "a.ll").toFile()
        outf.createNewFile()
        outf.writeText(llvmifiedFiles)

        // invoke llvm on it
        "$LLC -filetype=obj a.ll".runCommand()
        "$CLANG a.o -o a".runCommand()
    }

    companion object {
        val LLC = "/usr/local/opt/llvm/bin/llc"
        val CLANG = "/usr/local/opt/llvm/bin/clang"
    }
}