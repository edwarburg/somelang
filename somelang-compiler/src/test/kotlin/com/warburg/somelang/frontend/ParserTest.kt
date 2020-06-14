package com.warburg.somelang.frontend

import org.junit.jupiter.api.Test

/**
 * @author ewarburg
 */
class ParserTest {
    @Test
    fun `can parse func decl`() {
        val input = "fun hello() { 123 }"
        val result = input.parse()
        println(result)
    }
}
