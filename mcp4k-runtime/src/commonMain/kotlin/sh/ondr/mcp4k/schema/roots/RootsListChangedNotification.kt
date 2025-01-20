package sh.ondr.mcp4k.schema.roots

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.JsonRpcNotification
import sh.ondr.mcp4k.schema.core.NotificationParams

/**
 * A notification from the client to the server, informing that the list of roots has changed.
 * The server may now request `roots/list` again to get the updated set of roots.
 */
@Serializable
@SerialName("notifications/roots/list_changed")
data class RootsListChangedNotification(
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
