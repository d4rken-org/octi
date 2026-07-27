package eu.darken.octi.common.http

import javax.inject.Qualifier

/**
 * Marks the `OkHttpClient` dedicated to streaming transfers (blob upload/download).
 *
 * The default client carries a total `callTimeout`, which spans response-body consumption. Blob
 * bodies are handed out and consumed later (decryption in the blob store), so that bound would
 * truncate large or slow transfers. This client uses per-socket timeouts only and header-level
 * logging so the logging interceptor can't buffer a streaming body into memory.
 */
@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class StreamingClient
