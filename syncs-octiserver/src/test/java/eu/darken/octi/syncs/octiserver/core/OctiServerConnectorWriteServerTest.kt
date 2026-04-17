package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncWrite
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class OctiServerConnectorWriteServerTest : BaseTest() {

    private val serverId = "octiserver-prod-acc1"
    private val gdriveId = "gdrive-default-user"

    private fun attachment(
        logicalKey: String,
        connectorRefs: Map<String, RemoteBlobRef> = emptyMap(),
        availableOn: Set<String> = emptySet(),
    ) = SyncWrite.BlobAttachment(
        logicalKey = logicalKey,
        connectorRefs = connectorRefs,
        availableOn = availableOn,
    )

    @Test
    fun `includes attachments with a matching connector ref`() {
        val attachments = listOf(
            attachment(
                logicalKey = "blob-1",
                connectorRefs = mapOf(serverId to RemoteBlobRef("srv-remote-1")),
                availableOn = setOf(serverId),
            ),
            attachment(
                logicalKey = "blob-2",
                connectorRefs = mapOf(serverId to RemoteBlobRef("srv-remote-2")),
                availableOn = setOf(serverId),
            ),
        )
        filterResidentBlobs(attachments, serverId) shouldBe listOf("srv-remote-1", "srv-remote-2")
    }

    @Test
    fun `skips attachments without this connector's ref (no logicalKey fallback)`() {
        val attachments = listOf(
            attachment(
                logicalKey = "blob-gdrive-only",
                connectorRefs = mapOf(gdriveId to RemoteBlobRef("blob-gdrive-only")),
                availableOn = setOf(gdriveId),
            ),
            attachment(
                logicalKey = "blob-server",
                connectorRefs = mapOf(serverId to RemoteBlobRef("srv-remote-server")),
                availableOn = setOf(serverId),
            ),
        )
        filterResidentBlobs(attachments, serverId) shouldBe listOf("srv-remote-server")
    }

    @Test
    fun `filters out stale refs whose connector is not in availableOn`() {
        // Bug-surface regression guard: if connectorRefs and availableOn drift out of sync
        // (e.g. a cleanup path only updated one of them), the filter must not ship a stale ref.
        val attachments = listOf(
            attachment(
                logicalKey = "blob-stale",
                connectorRefs = mapOf(serverId to RemoteBlobRef("srv-stale-ref")),
                availableOn = setOf(gdriveId),
            ),
        )
        filterResidentBlobs(attachments, serverId) shouldBe emptyList()
    }

    @Test
    fun `empty availableOn disables the filter (backward compat)`() {
        val attachments = listOf(
            attachment(
                logicalKey = "blob-legacy",
                connectorRefs = mapOf(serverId to RemoteBlobRef("srv-legacy-ref")),
                availableOn = emptySet(),
            ),
        )
        filterResidentBlobs(attachments, serverId) shouldBe listOf("srv-legacy-ref")
    }

    @Test
    fun `empty attachments returns empty list`() {
        filterResidentBlobs(emptyList(), serverId) shouldBe emptyList()
    }

    @Test
    fun `preserves order of attachments`() {
        val attachments = listOf(
            attachment("b1", mapOf(serverId to RemoteBlobRef("r1")), setOf(serverId)),
            attachment("b2", mapOf(serverId to RemoteBlobRef("r2")), setOf(serverId)),
            attachment("b3", mapOf(serverId to RemoteBlobRef("r3")), setOf(serverId)),
        )
        filterResidentBlobs(attachments, serverId) shouldBe listOf("r1", "r2", "r3")
    }
}
