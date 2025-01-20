package sh.ondr.mcp4k.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.JsonRpcNotification
import sh.ondr.mcp4k.schema.core.NotificationParams

/**
 * A notification from the server to the client that the list of tools has changed.
 * The client may issue a `tools/list` request to get the updated list.
 */
@Serializable
@SerialName("notifications/tools/list_changed")
data class ToolListChangedNotification(
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
