package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import com.warburg.somelang.middleend.*
import com.warburg.somelang.runCommand
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import java.io.File
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
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(lit(123))
                    }
                }
            }
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("123", node, tc, idResolver)
        }
        test("return int from main with variable") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = block {
                            statements {
                                +localVarDeclaration {
                                    nameNode = id("a")
                                    rhs = lit(123)
                                    type = TypeNameNode(id("Int"))
                                }
                                +ret(readLocal("a"))
                            }
                        }
                    }
                }
            }
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("123", node, tc, idResolver)
        }
        test("return int from main with math") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = block {
                            statements {
                                +localVarDeclaration {
                                    nameNode = id("a")
                                    rhs = lit(123)
                                    type = TypeNameNode(id("Int"))
                                }
                                +localVarDeclaration {
                                    nameNode = id("b")
                                    rhs = lit(456)
                                    type = TypeNameNode(id("Int"))
                                }
                                +localVarDeclaration {
                                    nameNode = id("c")
                                    rhs = lit(789)
                                    type = TypeNameNode(id("Int"))
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
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("1368", node, tc, idResolver)
        }
        test("invoke another function") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(lit(123))
                    }
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = emptyList()
                        })
                    }
                }
            }
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            idResolver.resolveIdTo(unresolved("targetFunc"), fqn("targetFunc"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), IntType))
            tc.putType(fqn("targetFunc"), FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("123", node, tc, idResolver)
        }
        test("invoke another function with an argument") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = listOf(NormalParameterDeclarationNode(id("a"), TypeNameNode(id("Int"))))
                        returnType = TypeNameNode(id("Int"))
                        body = ret(binOp(BinaryOperator.ADD, readLocal("a"), lit(456)))
                    }
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = listOf(pos(lit(123)))
                        })
                    }
                }
            }
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            idResolver.resolveIdTo(unresolved("targetFunc"), fqn("targetFunc"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), IntType))
            tc.putType(fqn("targetFunc"), FunctionType(listOf(ArgumentType("a", IntType)), IntType))
            convertToJvmBytecodeAndRun("579", node, tc, idResolver)
        }
        test("invoke another function with a string argument") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = listOf(NormalParameterDeclarationNode(id("a"), TypeNameNode(id("String"))))
                        returnType = TypeNameNode(id("String"))
                        body = ret(readLocal("a"))
                    }
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("String"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = listOf(pos(lit("abc")))
                        })
                    }
                }
            }
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            idResolver.resolveIdTo(unresolved("targetFunc"), fqn("targetFunc"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), StringType))
            tc.putType(fqn("targetFunc"), FunctionType(listOf(ArgumentType("a", StringType)), StringType))
            convertToJvmBytecodeAndRun("abc", node, tc, idResolver)
        }
        test("return string from main with variable") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("String"))
                        body = block {
                            statements {
                                +localVarDeclaration {
                                    nameNode = id("a")
                                    rhs = lit("abc")
                                    type = TypeNameNode(id("String"))
                                }
                                +ret(readLocal("a"))
                            }
                        }
                    }
                }
            }
            val idResolver = MapIdResolver()
            idResolver.resolveIdTo(unresolved("somelangMain"), fqn("somelangMain"))
            val tc = TypeContext()
            tc.putType(fqn("somelangMain"), FunctionType(listOf(), StringType))
            convertToJvmBytecodeAndRun("abc", node, tc, idResolver)
        }
    }
})

private fun convertToJvmBytecodeAndRun(expected: String, node: FileNode, tc: TypeContext, idResolver: MapIdResolver) {
    val outPath = Path.of("/Users/ewarburg/Desktop/output.jar")
    convertToJvmBytecode(CompilationInput(listOf(node), tc, idResolver, outPath))
    val stdout = File.createTempFile("testStdout", ".txt")
    val stderr = File.createTempFile("testStderr", ".txt")
    "java -jar $outPath".runCommand(stdout = stdout, stderr = stderr)

    val error = stderr.readText()
    if (error.isNotEmpty()) {
        fail("Expected command to finish successfully, but it wrote to stderr:\n$error")
    }
    val output = stdout.readText()
    output.trim() shouldBe expected
}