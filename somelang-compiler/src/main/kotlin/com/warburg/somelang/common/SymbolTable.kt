package com.warburg.somelang.common

import java.util.*

/**
 * @author ewarburg
 */
private typealias Frame<Key, Value> = MutableMap<Key, Value>

class SymbolTable<Key, Value> {
    private val stack: MutableList<Frame<Key, Value>> = ArrayList()
    private val opaque: MutableList<Boolean> = ArrayList()

    fun pushOpaqueFrame() {
        pushFrame(true)
    }

    fun pushTransparentFrame() {
        pushFrame(false)
    }

    fun pushFrame(opaque: Boolean) {
        this.stack.add(mutableMapOf())
        this.opaque.add(opaque)
    }

    fun popFrame() {
        val lastIndex = this.stack.size - 1
        this.stack.removeAt(lastIndex)
        this.opaque.removeAt(lastIndex)
    }

    fun lookup(key: Key): Value? {
        if (this.stack.isEmpty()) {
            return null
        }

        var currentFrameIdx = this.stack.size - 1
        var currentFrame: Frame<Key, Value>
        do {
            currentFrame = this.stack[currentFrameIdx]
            val value = currentFrame[key]
            if (value != null) {
                return value
            }
            currentFrameIdx--
        } while (currentFrameIdx >= 0 && !this.opaque[currentFrameIdx])

        return null
    }

    fun put(key: Key, value: Value) {
        this.stack.last()[key] = value
    }

    inline fun lookupOrPut(key: Key, valueSupplier: () -> Value): Value = lookup(key) ?: run {
        val value = valueSupplier()
        put(key, value)
        return value
    }
}

inline fun <A> SymbolTable<*, *>.withFrame(opaque: Boolean, action: () -> A): A {
    try {
        pushFrame(opaque)
        return action()
    } finally {
        popFrame()
    }
}

inline fun <A> SymbolTable<*, *>.withOpaqueFrame(action: () -> A): A = withFrame(true, action)
inline fun <A> SymbolTable<*, *>.withTransparentFrame(action: () -> A): A = withFrame(false, action)