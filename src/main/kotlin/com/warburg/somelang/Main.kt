package com.warburg.somelang

import com.warburg.somelang.compiler.Compiler
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * @author ewarburg
 */
fun main(args: Array<String>) {
    val compiler = Compiler(Path.of(args[0]), Path.of(args[1]))
    compiler.compile()
    "${Compiler.LLC} -filetype=asm a.ll".runCommand()
    "cat a.s".runCommand()
    println("\n\nrunning ./a")
    "time ./a".runCommand()
}

fun String.runCommand(workingDir: File = File(System.getProperty("user.dir")), stdout: File? = null, stderr: File? = null) = getCommandProcess(workingDir, stdout, stdout).waitFor(60, TimeUnit.MINUTES)

fun String.getCommandProcess(workingDir: File = File(System.getProperty("user.dir")), stdout: File? = null, stderr: File? = null): Process {
    val builder = ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
    if (stdout == null) {
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    } else {
        builder.redirectOutput(stdout)
    }
    if (stderr == null) {
        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
    } else {
        builder.redirectError(stderr)
    }
    return builder.start()
}