package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import com.warburg.somelang.runCommand
import io.kotlintest.specs.FunSpec
import java.nio.file.Path

/**
 * @author ewarburg
 */
class FileToJvmBytecodeTest : FunSpec({
    context("functions") {
        test("return int from main") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = id("somelangMain")
                        body = ret(lit(123))
                    }
                }
            }
            convertToJvmBytecodeAndRun(node)
        }
        test("return int from main with variable") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = id("somelangMain")
                        body = block {
                            statements {
                                +localVarDeclaration {
                                    name = id("a")
                                    rhs = lit(123)
                                }
                                +ret(readLocal("a"))
                            }
                        }
                    }
                }
            }
            convertToJvmBytecodeAndRun(node)
        }
        test("return int from main with math") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = id("somelangMain")
                        body = block {
                            statements {
                                +localVarDeclaration {
                                    name = id("a")
                                    rhs = lit(123)
                                }
                                +localVarDeclaration {
                                    name = id("b")
                                    rhs = lit(456)
                                }
                                +localVarDeclaration {
                                    name = id("c")
                                    rhs = lit(789)
                                }
                                +ret(
                                    add(readLocal("a"),
                                        add(
                                            readLocal("b"),
                                            readLocal("c")
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
            convertToJvmBytecodeAndRun(node)
        }
    }
})

private fun convertToJvmBytecodeAndRun(node: FileNode) {
    val outPath = Path.of("/Users/ewarburg/Desktop/output.jar")
    convertToJvmBytecode(listOf(node), outPath)
    "java -jar $outPath".runCommand()
}