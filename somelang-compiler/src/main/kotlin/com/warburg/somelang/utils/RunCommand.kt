package com.warburg.somelang.utils

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * @author ewarburg
 */
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