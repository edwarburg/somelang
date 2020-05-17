package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import com.warburg.somelang.middleend.*
import com.warburg.somelang.runCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.File
import java.nio.file.Files
import com.warburg.somelang.ast.typeConstDecl as typeConst

val theFileFqn = fqn("com.somelang.TheFile")
val somelangMainFqn = fqn("${theFileFqn.text}.somelangMain")
val targetFuncFqn = fqn("${theFileFqn.text}.targetFunc")
val fooFqn = theFileFqn.qualifyingSegment + "Foo"
val barFqn = fooFqn + "Bar"
val bazFqn = fooFqn + "Baz"
/**
 * @author ewarburg
 */
class FileToJvmBytecodeTest {

    @Nested
    inner class Functions {
        @Test
        fun `return int from main`() {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(lit(123))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("123", tc, node)
        }

        @Test
        fun `return int from main with variable`() {
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
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("123", tc, node)
        }

        @Test
        fun `return int from main with math`() {
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
                                    add(
                                        readLocal("a"),
                                        add(
                                            readLocal("b"),
                                            readLocal("c")
                                        )
                                    )
                                )
                            }
                        }
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("1368", tc, node)
        }

        @Test
        fun `invoke another function`() {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(lit(123))
                    }.withDeclFqn(targetFuncFqn)
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = emptyList()
                        }.withReferentFqn(targetFuncFqn))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), IntType))
            tc.putType(targetFuncFqn, FunctionType(listOf(), IntType))
            convertToJvmBytecodeAndRun("123", tc, node)
        }

        @Test
        fun `invoke another function with an argument`() {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = listOf(NormalParameterDeclarationNode(id("a"), TypeNameNode(id("Int"))))
                        returnType = TypeNameNode(id("Int"))
                        body = ret(binOp(BinaryOperator.ADD, readLocal("a"), lit(456)))
                    }.withDeclFqn(targetFuncFqn)
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Int"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = listOf(pos(lit(123)))
                        }.withReferentFqn(targetFuncFqn))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), IntType))
            tc.putType(targetFuncFqn, FunctionType(listOf(ArgumentType("a", IntType)), IntType))
            convertToJvmBytecodeAndRun("579", tc, node)
        }

        @Test
        fun `invoke another function with a string argument`() {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = listOf(NormalParameterDeclarationNode(id("a"), TypeNameNode(id("String"))))
                        returnType = TypeNameNode(id("String"))
                        body = ret(readLocal("a"))
                    }.withDeclFqn(targetFuncFqn)
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("String"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = listOf(pos(lit("abc")))
                        }.withReferentFqn(targetFuncFqn))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), StringType))
            tc.putType(targetFuncFqn, FunctionType(listOf(ArgumentType("a", StringType)), StringType))
            convertToJvmBytecodeAndRun("abc", tc, node)
        }

        @Test
        fun `invoke another function in another file`() {
            val file2fqn = fqn("some.other.FileThingy")
            val targetFuncInFile2Fqn = file2fqn + "targetFunc"

            val file1Node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("String"))
                        body = ret(invoke {
                            target = id("targetFunc")
                            arguments = listOf(pos(lit("abc")))
                        }.withReferentFqn(targetFuncInFile2Fqn))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)

            val file2node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id("targetFunc")
                        parameters = listOf(NormalParameterDeclarationNode(id("a"), TypeNameNode(id("String"))))
                        returnType = TypeNameNode(id("String"))
                        body = ret(readLocal("a"))
                    }.withDeclFqn(targetFuncInFile2Fqn)
                }
            }.withDeclFqn(file2fqn)

            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), StringType))
            tc.putType(targetFuncInFile2Fqn, FunctionType(listOf(ArgumentType("a", StringType)), StringType))
            convertToJvmBytecodeAndRun("abc", tc, file1Node, file2node)
        }

        @Test
        fun `return string from main with variable`() {
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
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), StringType))
            convertToJvmBytecodeAndRun("abc", tc, node)
        }
    }

    @Nested
    inner class Classes {
        @Test
        fun `basic class with one constructor`() {
            val node = file {
                nodes {
                    +dataDecl {
                        typeConstructorDeclaration = typeConst {
                            nameNode = id("Foo")
                        }.withDeclFqn(fooFqn)
                        valueConstructorDeclarations {
                            +valueConstDecl {
                                nameNode = id("Bar")
                            }.withDeclFqn(barFqn)
                        }
                    }.withDeclFqn(fooFqn)
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Foo"))
                        body = ret(valueConstInvoke {
                            target = id("Bar")
                        }.withReferentFqn(barFqn))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), SomelangObjectType(fooFqn)))
            convertToJvmBytecodeAndRun("Bar", tc, node)
        }

        @Test
        fun `basic class with two constructors`() {
            val node = file {
                nodes {
                    +dataDecl {
                        typeConstructorDeclaration = typeConst {
                            nameNode = id("Foo")
                        }.withDeclFqn(fooFqn)
                        valueConstructorDeclarations {
                            +valueConstDecl {
                                nameNode = id("Bar")
                            }.withDeclFqn(barFqn)
                            +valueConstDecl {
                                nameNode = id("Baz")
                            }.withDeclFqn(bazFqn)
                        }
                    }.withDeclFqn(fooFqn)
                    +functionDeclaration {
                        nameNode = id("somelangMain")
                        parameters = emptyList()
                        returnType = TypeNameNode(id("Foo"))
                        body = ret(valueConstInvoke {
                            target = id("Baz")
                        }.withReferentFqn(bazFqn))
                    }.withDeclFqn(somelangMainFqn)
                }
            }.withDeclFqn(theFileFqn)
            val tc = TypeContext()
            tc.putType(somelangMainFqn, FunctionType(listOf(), SomelangObjectType(fooFqn)))
            convertToJvmBytecodeAndRun("Baz", tc, node)
        }
    }
}

private fun convertToJvmBytecodeAndRun(
    expected: String,
    tc: TypeContext,
    vararg nodes: FileNode
) {
    val outJar = File.createTempFile("output", ".jar")
    val outPath = outJar.toPath()
    val stdout = File.createTempFile("testStdout", ".txt")
    val stderr = File.createTempFile("testStderr", ".txt")
    println("Compiling to $outPath with stdout: $stdout and stderr: $stderr")
    convertToJvmBytecode(CompilationInput(nodes.toList(), tc, outPath))
    "java -jar $outPath".runCommand(stdout = stdout, stderr = stderr)

    val error = stderr.readText()
    if (error.isNotEmpty()) {
        fail("Expected command to finish successfully, but it wrote to stderr:\n$error")
    }

    val output = stdout.readText().trim()
    if (output != expected) {
        val unzippedDir = Files.createTempDirectory("output")
        val unzip = "unzip $outPath -d $unzippedDir"
        println(unzip)
        unzip.runCommand()
        val find = "find $unzippedDir -name *.class -exec echo {} ; -exec javap -c -verbose {} ;"
        println(find)
        find.runCommand()
        assertEquals(expected, output)
    }
}