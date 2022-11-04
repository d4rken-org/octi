package eu.darken.octi.module.core

import okio.ByteString

interface ModuleSerializer<T> {
    fun serialize(item: T): ByteString
    fun deserialize(raw: ByteString): T
}