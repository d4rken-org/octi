package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType

private const val CONNECTOR_LABEL_ID_CHARS = 8

/**
 * Returns the account label, suffixed with a short id when (type, accountLabel) clashes with
 * at least one other entry in [peerKeys]. Used by sync UI surfaces to disambiguate otherwise-
 * identical connector rows (e.g. two Octi Server accounts on the same domain).
 *
 * [peerKeys] should include the target itself; a clash is any count > 1.
 */
fun disambiguatedAccountLabel(
    type: ConnectorType,
    account: String,
    accountLabel: String,
    peerKeys: Iterable<Pair<ConnectorType, String>>,
): String {
    val matches = peerKeys.count { it.first == type && it.second == accountLabel }
    return if (matches > 1) "$accountLabel (${account.take(CONNECTOR_LABEL_ID_CHARS)})" else accountLabel
}
