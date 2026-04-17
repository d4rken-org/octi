package eu.darken.octi.sync.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-side logical identity of a blob. Stable across connectors; generated once on upload
 * by the creating device. Persisted in [eu.darken.octi.sync.core.SyncWrite.BlobAttachment.logicalKey]
 * and in the module payload (e.g. `FileShareInfo.SharedFile.blobKey`).
 */
@Serializable
data class BlobKey(@SerialName("id") val id: String)

/**
 * Connector-scoped remote reference. Opaque string the backend uses to locate a blob.
 *
 * - GDrive: equals the [BlobKey.id] (filename under `blob-store/{deviceId}/{moduleId}/`).
 * - OctiServer: server-generated blob id returned from the upload-session finalize call —
 *   **not** equal to [BlobKey.id].
 *
 * Callers should never parse this string; only pass it back to the matching [BlobStore].
 */
@JvmInline
@Serializable
value class RemoteBlobRef(val value: String)
