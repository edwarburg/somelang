package com.warburg.somelang.ast

/**
 * @author ewarburg
 */
interface Annotatable {
    val annotations: List<Annotation>

    fun <T : Annotation> getAnnotation(type: AnnotationType<T>): T? = this.annotations.firstOrNull { it.type == type } as? T?
}

interface Annotation {
    val type: AnnotationType<Annotation>
}
interface AnnotationType<T : Annotation>