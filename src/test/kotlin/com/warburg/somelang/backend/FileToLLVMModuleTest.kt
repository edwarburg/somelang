package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import io.kotlintest.specs.FunSpec

/**
 * @author ewarburg
 */
class FileToLLVMModuleTest : FunSpec({
    context("functions") {
        test("return int from main") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id(fqn("main"))
                        body = ReturnNode(IntLiteralNode(123))
                    }
                }
            }
            val result = convertToLLVM(listOf(node))
            println(result)
        }
        test("return int from main with variable") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id(fqn("main"))
                        body = block {
                            statements {
                                +LocalVarDeclarationNode(id(fqn("a")), IntLiteralNode(123))
                                +ReturnNode(id(fqn("a")))
                            }
                        }
                    }
                }
            }
            val result = convertToLLVM(listOf(node))
            println(result)
        }
        test("return int from main with math") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        nameNode = id(fqn("main"))
                        body = block {
                            statements {
                                +LocalVarDeclarationNode(id(fqn("a")), IntLiteralNode(123))
                                +LocalVarDeclarationNode(id(fqn("b")), IntLiteralNode(456))
                                +LocalVarDeclarationNode(id(fqn("c")), IntLiteralNode(789))
                                +ReturnNode(BinaryOperationNode(BinaryOperator.ADD, id(fqn("a")), BinaryOperationNode(BinaryOperator.ADD, readLocal("b"), readLocal("c"))))
                            }
                        }
                    }
                }
            }
            val result = convertToLLVM(listOf(node))
            println(result)
        }
    }
})


