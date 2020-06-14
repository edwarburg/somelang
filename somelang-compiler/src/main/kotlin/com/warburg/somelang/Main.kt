package com.warburg.somelang

import com.warburg.somelang.compiler.Compiler
import java.nio.file.Path

/**
 * @author ewarburg
 */
fun main(args: Array<String>) {
    val compiler = Compiler(Path.of(args[0]), Path.of(args[1]))
    compiler.compile()
}

