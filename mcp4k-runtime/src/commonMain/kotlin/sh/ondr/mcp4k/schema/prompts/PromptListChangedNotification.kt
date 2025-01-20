package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.JsonRpcNotification
import sh.ondr.mcp4k.schema.core.NotificationParams

/**
 * A notification from the server to the client that the list of prompts has changed.
 * The client may issue a `prompts/list` request to get the updated list.
 */
@Serializable
@SerialName("notifications/prompts/list_changed")
data class PromptListChangedNotification(
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
