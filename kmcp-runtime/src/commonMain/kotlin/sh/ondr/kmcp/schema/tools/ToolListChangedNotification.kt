package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.NotificationParams

/**
 * A notification from the server to the client that the list of tools has changed.
 * The client may issue a `tools/list` request to get the updated list.
 */
@Serializable
@SerialName("notifications/tools/list_changed")
data class ToolListChangedNotification(
	override val method: String = "notifications/tools/list_changed",
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
