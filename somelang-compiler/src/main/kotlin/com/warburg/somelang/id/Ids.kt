package com.warburg.somelang.id

import com.warburg.somelang.middleend.IdResolver

interface Name {
    val text: String
}

/**
 * @author ewarburg
 */
inline class FullyQualifiedName(override val text: String) : Name {
    val qualifyingSegment: FullyQualifiedName
        get() = FullyQualifiedName(run {
            when (val lastDot = this.text.lastIndexOf(".")) {
                -1 -> ""
                this.text.length - 1 -> this.text.substring(0, this.text.length - 1)
                else -> this.text.substring(0, lastDot)
            }
        })
    val finalSegment: String
        get() = run {
            when (val lastDot = this.text.lastIndexOf(".")) {
                -1 -> this.text
                this.text.length - 1 -> ""
                else -> this.text.substring(lastDot + 1, this.text.length)
            }
        }

    operator fun plus(other: String): FullyQualifiedName = FullyQualifiedName("${this.text}.$other")
}

inline class UnresolvedName(override val text: String) : Name

fun Name.resolve(idResolver: IdResolver): FullyQualifiedName? = when (this) {
    is FullyQualifiedName -> this
    else -> idResolver.resolveId(this)
}
