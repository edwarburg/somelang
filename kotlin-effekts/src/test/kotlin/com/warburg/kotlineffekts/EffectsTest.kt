package com.warburg.kotlineffekts

import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.DebugProbes.withDebugProbes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Duration

val defaultTimeout = Duration.ofSeconds(1)

/**
 * @author ewarburg
 */
class EffectsTest {
    @Test
    fun `effects returning simple result`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    42
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effects returning simple result with NoOp returning handler never invoked`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(NoOp) { resume(Unit) }
                run {
                    42
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effects returning simple result with NoOp returning handler invoked once`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(NoOp) { resume(Unit) }
                run {
                    perform(NoOp, Unit)
                    42
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effect which does not resume`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(NoOp) {
                    fallthrough(1)
                }
                run {
                    perform(NoOp, Unit)
                    throw UnsupportedOperationException("unreachable")
                }
            }
            assertEquals(1, result)
        }
    }

    @Test
    fun `effect which resumes with value`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(Add1) { n ->
                    resume(n + 1)
                }
                run {
                    perform(Add1, 41)
                }
            }
            assertEquals(42, result)
        }
    }
    @Test
    fun `effect which resumes multiple times`() {
        withTimeout {
            val list = mutableListOf<Int>()
            val result = withEffects<Int> {
                handler(Add1) { n ->
                    if (list.isEmpty()) {
                        list.add(n)
                    }
                    val n1 = n + 1
                    list.add(n1)
                    resume(n1)
                }
                run {
                    var result = 0
                    while (result < 42) {
                        result = perform(Add1, result)
                    }
                    result
                }
            }
            assertEquals(42, result)
            assertEquals((0..42).toList(), list)
        }
    }
    
    @Test
    fun `finally in run() body never runs when fallthrough is called`() {
        withTimeout {
            withEffects<Unit> {
                handler(NoOp) {
                    fallthrough(Unit)
                }
                run {
                    try {
                        perform(NoOp, Unit)
                    } finally {
                        fail("ran finally")
                    }
                }
            }
        }
    }

    @Test
    fun `user exceptions bubble up`() {
        withTimeout {
            val caught: Boolean
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        resume(Unit)
                    }
                    run {
                        throw UserException()
                    }
                }
                fail("did not throw exception")
            } catch (e: UserException) {
                caught = true
            }

            assertTrue(caught, "user exception was not caught")
        }
    }

    @Test
    fun `handler exceptions bubble up when uncaught by user`() {
        withTimeout {
            val caught: Boolean
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        throw HandlerException()
                    }
                    run {
                        perform(NoOp, Unit)
                    }
                }
                fail("did not throw exception")
            } catch (e: HandlerException) {
                caught = true
            }

            assertTrue(caught, "handler exception was not caught")
        }
    }

    @Test
    fun `exceptions thrown in handler cannot be caught by user code`() {
        withTimeout {
            val caught: Boolean
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        throw HandlerException()
                    }
                    run {
                        try {
                            perform(NoOp, Unit)
                        } catch (e: HandlerException) {
                            fail("user code caught handler exception")
                        }
                    }
                }
                fail("did not throw exception")
            } catch (e: HandlerException) {
                caught = true
            }

            assertTrue(caught, "handler exception was not caught")
        } 
    }

    @Test
    fun `handler can cause user code to fail with exception`() {
        withTimeout {
            var caught = false
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        resumeWithException(HandlerException())
                    }
                    run {
                        try {
                            perform(NoOp, Unit)
                        } catch (e: HandlerException) {
                            caught = true
                        }
                    }
                }
            } catch (e: HandlerException) {
                fail("Exception bubbled up out of `withEffects`")
            }

            assertTrue(caught, "handler exception was not caught in user code")
        }
    }

    @Test
    fun `nested user code can perform effects`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(GiveMeTheAnswer) {
                    resume(42)
                }
                run {
                    giveMeTheAnswer()
                }
            }

            assertEquals(42, result)
        }
    }

    fun giveMeTheAnswer(): Int {
        return perform(GiveMeTheAnswer, Unit)
    }

    object Get: Effect<Unit, Any> {}
    object Put: Effect<Any, Unit> {}

    @Test
    fun `state test`() {
        val actual = indexed(listOf("a", "b", "c"))
        assertEquals(listOf(0 to "a", 1 to "b", 2 to "c"), actual)
    }

    private fun <S : Any, T> state(initial: S, comp: () -> T): T {
        return withEffects {
            var cell = initial
            run {
                comp()
            }
            handler(Get) {
                resume(cell)
            }
            handler(Put) {
                cell = it as S
                resume(Unit)
            }
        }
    }

    private fun next(): Int {
        val last = perform(Get, Unit) as Int
        perform(Put, last + 1)
        return last
    }

    fun <A> indexed(list: List<A>): List<Pair<Int, A>> {
        return state(0) { list.map { next() to it } }
    }
    
    private fun withTimeout(duration: Duration = defaultTimeout, block: () -> Unit) {
        withDebugProbes {
            assertTimeoutPreemptively(duration, block) {
                val baos = ByteArrayOutputStream()
                val strStream = PrintStream(baos, true, StandardCharsets.UTF_8.name())
                DebugProbes.dumpCoroutines(strStream)
                baos.toString(StandardCharsets.UTF_8)
            }
        }
    }
}

class UserException : RuntimeException()
class HandlerException : RuntimeException()

object NoOp : Effect<Unit, Unit>
object Add1 : Effect<Int, Int>
object GiveMeTheAnswer : Effect<Unit, Int>
