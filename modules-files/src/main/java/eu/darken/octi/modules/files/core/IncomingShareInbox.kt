package eu.darken.octi.modules.files.core

import android.net.Uri
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token-keyed handoff for URIs delivered via the system share sheet (`ACTION_SEND` /
 * `ACTION_SEND_MULTIPLE`). MainActivityVM enqueues URIs and obtains a token; the navigation route
 * carries the token to FileShareListScreen, which calls [drain] exactly once to consume the batch.
 *
 * Tokens are required because rotation, multiple intents in flight, or rapid re-shares can race a
 * `replay=1` SharedFlow and either leak stale state or drop batches. ConcurrentHashMap + UUID
 * tokens guarantee one batch per token, drained exactly once.
 */
@Singleton
class IncomingShareInbox @Inject constructor() {

    private val pending = ConcurrentHashMap<String, List<Uri>>()

    /**
     * Stores [uris] under a fresh UUID token and returns the token. The caller is expected to
     * pass the token through the navigation route so [drain] can recover the batch on the
     * receiving screen.
     */
    fun enqueue(uris: List<Uri>): String {
        val token = UUID.randomUUID().toString()
        pending[token] = uris
        return token
    }

    /**
     * Returns and removes the URI batch keyed by [token]. Returns `null` for unknown / already-
     * drained tokens — idempotent on repeat calls.
     */
    fun drain(token: String): List<Uri>? = pending.remove(token)
}
