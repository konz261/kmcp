package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.JsonRpcNotification
import sh.ondr.mcp4k.schema.core.NotificationParams

/**
 * A notification from the server to the client that the list of resources has changed.
 * The client should issue a `resources/list` request to get the updated list.
 */
@Serializable
@SerialName("notifications/resources/list_changed")
data class ResourceListChangedNotification(
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
