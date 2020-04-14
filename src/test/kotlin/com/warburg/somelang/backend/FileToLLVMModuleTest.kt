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
                        name = Identifier("main")
                        body = Return(IntLiteral(123))
                    }
                }
            }
            val result = convert(listOf(node))
            println(result)
        }
        test("return int from main with variable") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = Identifier("main")
                        body = block {
                            statements {
                                +LocalVarDeclaration(Identifier("a"), IntLiteral(123))
                                +Return(Identifier("a"))
                            }
                        }
                    }
                }
            }
            val result = convert(listOf(node))
            println(result)
        }
        test("return int from main with math") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = Identifier("main")
                        body = block {
                            statements {
                                +LocalVarDeclaration(Identifier("a"), IntLiteral(123))
                                +LocalVarDeclaration(Identifier("b"), IntLiteral(456))
                                +LocalVarDeclaration(Identifier("c"), IntLiteral(789))
                                +Return(BinaryOperation(BinaryOperator.ADD, Identifier("a"), BinaryOperation(BinaryOperator.ADD, Identifier("b"), Identifier("c"))))
                            }
                        }
                    }
                }
            }
            val result = convert(listOf(node))
            println(result)
        }
    }
})


