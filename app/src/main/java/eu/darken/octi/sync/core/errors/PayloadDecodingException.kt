package eu.darken.octi.sync.core.errors

import eu.darken.octi.sync.core.SyncRead

class PayloadDecodingException(
    syncRead: SyncRead.Device.Module
) : IllegalArgumentException("Failed to decode $syncRead")