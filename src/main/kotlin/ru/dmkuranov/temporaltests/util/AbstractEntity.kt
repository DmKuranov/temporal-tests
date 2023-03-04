package ru.dmkuranov.temporaltests.util

import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version

@MappedSuperclass
abstract class AbstractEntity {
    abstract var id: Long?

    @Suppress("unused")
    @Version
    var version: Long? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as AbstractEntity
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        var hashCode = 13
        hashCode += if (null == id) 0 else id!!.hashCode() * 31
        return hashCode
    }

    override fun toString(): String = "${this.javaClass.simpleName}#$id"
}
