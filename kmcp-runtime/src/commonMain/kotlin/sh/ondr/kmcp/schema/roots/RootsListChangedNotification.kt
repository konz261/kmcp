package sh.ondr.kmcp.schema.roots

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.NotificationParams

/**
 * A notification from the client to the server, informing that the list of roots has changed.
 * The server may now request `roots/list` again to get the updated set of roots.
 */
@Serializable
@SerialName("notifications/roots/list_changed")
data class RootsListChangedNotification(
	override val method: String = "notifications/roots/list_changed",
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
