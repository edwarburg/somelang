package com.warburg.somelang.middleend

import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name

/**
 * Takes an unresolved name and resolves it
 * @author ewarburg
 */
interface IdResolver {
    fun resolveId(name: Name): FullyQualifiedName?
    fun resolveIdTo(name: Name, fqn: FullyQualifiedName)
    fun unresolveId(name: Name)
}

// TODO more complex contextual name resolution. Should probably do that pass during type analysis

class MapIdResolver(input: Map<Name, FullyQualifiedName> = emptyMap()) : IdResolver {
    private val map: MutableMap<Name, FullyQualifiedName> = HashMap(input)

    override fun resolveId(name: Name): FullyQualifiedName? = this.map[name]

    override fun resolveIdTo(name: Name, fqn: FullyQualifiedName) {
        this.map[name] = fqn
    }

    override fun unresolveId(name: Name) {
        this.map.remove(name)
    }
}