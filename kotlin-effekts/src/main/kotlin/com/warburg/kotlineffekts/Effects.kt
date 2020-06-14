package com.warburg.kotlineffekts

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.Executor

// TODO n-ary effects

/**
 * @author ewarburg
 */
interface Effect<A, R>

interface Resumer<R> {
    // TODO can we get rid of the suspend here? want to hide coroutines as much as possible...
    suspend fun resume(result: R): MustCallResumeOrFallthrough
    suspend fun resumeWithException(cause: Throwable): MustCallResumeOrFallthrough
    fun <T> fallthrough(value: T): MustCallResumeOrFallthrough
}

/**
 * This is a marker class which is used solely to enforce that `EffectHandlerBlock`s must end by either calling `Resumer::resume`,
 * `Resumer::resumeWithException` or `Resumer::fallthrough`. The value cannot be constructed, and its only instance is
 * internal and returned only by `Resumer` implementations.
 */
class MustCallResumeOrFallthrough private constructor() {
    companion object {
        internal val INSTANCE = MustCallResumeOrFallthrough()
    }
}

private class ChannelResumer<R>(private val down: Channel<MyResult<out R>>):
    Resumer<R> {
    override suspend fun resume(result: R): MustCallResumeOrFallthrough {
        this.down.send(Success(result))
        // unreachable, but they don't need to know that...
        return MustCallResumeOrFallthrough.INSTANCE
    }

    override suspend fun resumeWithException(cause: Throwable): MustCallResumeOrFallthrough {
        this.down.send(Failure(cause))
        return MustCallResumeOrFallthrough.INSTANCE
    }

    override fun <T> fallthrough(value: T): MustCallResumeOrFallthrough {
        throw FallThroughException(value)
    }
}

internal sealed class MyResult<R>
internal data class Success<R>(val value: R) : MyResult<R>()
internal data class Failure(val cause: Throwable) : MyResult<Nothing>()

typealias EffectHandlerBlock<A, R> = suspend Resumer<R>.(A) -> MustCallResumeOrFallthrough

internal data class EffectHandler<A, R>(val handler: EffectHandlerBlock<A, R>, val up: Channel<EffectInvocation<A, R>>)

internal data class EffectInvocation<A, R>(val effect: Effect<A, R>, val arg: A, val down: Channel<MyResult<out R>>)

private object CurrentThreadExecutor : Executor {
    override fun execute(command: Runnable) {
        command.run()
    }
}

// EffectsContexts are thread local, and coroutine scope always runs coroutines on the current thread.
// The net effect should be that all this coroutines and channels stuff doesn't leave the current thread, and all nested
// coroutines will always execute on the same thread.
// TODO better way to handle this than ThreadLocal?
private val threadlocalEffectsContext = ThreadLocal.withInitial {
    EffectsContext(
        CoroutineScope(CurrentThreadExecutor.asCoroutineDispatcher())
    )
}
//private val threadlocalEffectsContext = ThreadLocal.withInitial { EffectsContext(CoroutineScope(EmptyCoroutineContext)) }
internal fun getContext(): EffectsContext = threadlocalEffectsContext.get()

class FallThroughException(val value: Any?): RuntimeException()

class EffectsContext(internal val scope: CoroutineScope) {
    private val handlers: Deque<MutableMap<Effect<*, *>, EffectHandler<*, *>>> = ArrayDeque()
    private val results: Deque<Channel<MyResult<*>>> = ArrayDeque()

    internal fun pushScope() {
        this.handlers.push(mutableMapOf())
        this.results.push(Channel())
    }

    internal fun popScope() {
        val map = this.handlers.pollFirst()
        if (map != null) {
            for (handler in map.values) {
                handler.up.close()
            }
        }
        this.results.pollFirst()
    }

    internal fun <A, R> addHandler(effect: Effect<A, R>, handler: EffectHandler<A, R>) {
        this.handlers.peekFirst()[effect] = handler
        spawnHandler(effect, handler)
    }

    private fun <A, R> spawnHandler(effect: Effect<A, R>, handler: EffectHandler<A, R>) {
        this.scope.launch(CoroutineName("handler: $effect (${this.handlers.size})")) {
            for (invocation in handler.up) {
                try {
                    handler.handler.invoke(ChannelResumer(invocation.down), invocation.arg)
                } catch (e: FallThroughException) {
                    // yes, yes, exceptions as flow control are evil...
                    sendResult(e.value)
                    break
                } catch (t: Throwable) {
                    sendFailure(t)
                    break
                }
            }
        }
    }

    internal suspend fun sendResult(result: Any?) {
        this.results.peekFirst().send(Success(result))
    }

    internal suspend fun sendFailure(cause: Throwable) {
        this.results.peekFirst().send(Failure(cause))
    }

    internal fun <R> awaitResult(): R {
        try {
            return runBlocking(CoroutineName("EffectsContext await result")) {
                @Suppress("UNCHECKED_CAST")
                when (val result = results.peekFirst().receive()) {
                    is Success<*> -> result.value as R
                    is Failure -> throw result.cause
                }
            }
        } finally {
            results.pollFirst()!!.close()
        }
    }

    fun <A, R> perform(effect: Effect<A, R>, a: A): R {
        @Suppress("UNCHECKED_CAST")
        val handler: EffectHandler<A, R>? = this.handlers.lookup(effect) as? EffectHandler<A, R>?
        if (handler == null) {
            throw UnsupportedOperationException("no handler registered for effect $effect")
        } else {
            val down = Channel<MyResult<out R>>()
            val invocation = EffectInvocation(effect, a, down)
            var result: MyResult<out R>? = null
            this.scope.launch(CoroutineName("perform send invocation (${this.handlers.size})")) {
                handler.up.send(invocation)
                result = down.receive()
            }

            @Suppress("UNCHECKED_CAST")
            when (val r = result!!) {
                is Success<*> -> return r.value as R
                is Failure -> throw r.cause
            }
        }
    }

    private fun <K, V, M : Map<K, V>> Deque<M>.lookup(key: K): V? {
        if (isEmpty()) {
            return null
        }

        for (map in this) {
            val it = map[key]
            if (it != null) {
                return it
            }
        }
        return null
    }
}

@DslMarker
annotation class EffectsScopeDsl

class EffectsScopeBuilder<Result> internal constructor() {
    internal lateinit var block: EffectsContext.() -> Result

    fun <A, R> handler(effect: Effect<A, R>, handlerBlock: EffectHandlerBlock<A, R>) {
        val up = Channel<EffectInvocation<A, R>>()
        val handler = EffectHandler(handlerBlock, up)
        getContext().addHandler(effect, handler)
    }

    fun run(block: EffectsContext.() -> Result) {
        if (this::block.isInitialized) {
            throw UnsupportedOperationException("Can't register more than one block with `run {}`")
        }

        this.block = block
    }
}


@EffectsScopeDsl
fun <R> withEffects(init: EffectsScopeBuilder<R>.() -> Unit): R {
    val c = getContext()
    c.pushScope()
    try {
        val builder = EffectsScopeBuilder<R>()
        builder.init()

        c.scope.launch(CoroutineName("withEffects run block")) {
            try {
                val result = builder.block.invoke(c)
                c.sendResult(result)
            } catch (t: Throwable) {
                c.sendFailure(t)
            }
        }

        return c.awaitResult()
    } finally {
        c.popScope()
    }
}

@EffectsScopeDsl
fun <R> performing(block: EffectsContext.() -> R): R {
    return block.invoke(getContext())
}

fun <A, R> perform(effect: Effect<A, R>, a: A): R =
    performing { this.perform(effect, a) }