package eu.darken.octi.sync.core

class PayloadDecodingException(
    syncRead: SyncRead.Device.Module
) : IllegalArgumentException("Failed to decode $syncRead")