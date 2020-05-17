package com.warburg.somelang.common

import kotlin.reflect.KProperty

/**
 * @author ewarburg
 */
fun <A> nullable(): NullableProperty<A> =
    NullableProperty()

class NullableProperty<A> {
    private var theVal: A? = null

    val hasValue: Boolean
        get() = this.theVal != null

    fun clearValue() {
        this.theVal = null
    }

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): A = this.theVal ?: throw KotlinNullPointerException("Property ${prop.name} was read but hasn't been set (or was cleared)")

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: A) {
        this.theVal = value
    }
}