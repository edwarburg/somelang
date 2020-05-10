package com.warburg.somelang.id

import com.warburg.somelang.middleend.IdResolver

interface Name {
    val text: String
}

/**
 * @author ewarburg
 */
inline class FullyQualifiedName(override val text: String) : Name {
    val finalSegment: String
        get() = run {
            val lastDot = this.text.lastIndexOf(".")
            if (lastDot == -1) {
                return this.text
            }
            if (lastDot == this.text.length - 1) {
                return ""
            }
            return this.text.substring(lastDot + 1, this.text.length)
        }
}

inline class UnresolvedName(override val text: String) : Name

fun Name.resolve(idResolver: IdResolver): FullyQualifiedName? = when (this) {
    is FullyQualifiedName -> this
    else -> idResolver.resolveId(this)
}
