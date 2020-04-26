package com.warburg.somelang.attributable

/**
 * @author ewarburg
 */

interface AttrDef<A>
interface AttrVal<A> {
    val def: AttrDef<A>
    val value: A
}

class SimpleAttrVal<A>(override val def: AttrDef<A>, override val value: A) : AttrVal<A>

interface Attributable {
    val attributes: Collection<AttrVal<*>>
    val definitions: Collection<AttrDef<*>>
        get() = this.attributes.map { it.def }

    fun <A> hasAttribute(def: AttrDef<A>): Boolean = getAttributeValue(def) != null
    fun <A> getAttribute(def: AttrDef<A>): A? = getAttributeValue(def)?.value
    fun <A> getAttributeValue(def: AttrDef<A>): AttrVal<A>?
    fun <A> putAttribute(def: AttrDef<A>, value: A, attrValConstructor: (AttrDef<A>, A) -> AttrVal<A> = { def, v -> SimpleAttrVal(def, v) }) = putAttributeValue(attrValConstructor(def, value))
    fun <A> putAttributeValue(value: AttrVal<A>)
}