package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.NotificationParams

@Serializable
@SerialName("notifications/resources/updated")
data class ResourceUpdatedNotification(
	override val method: String = "notifications/resources/updated",
	override val params: ResourceUpdatedParams,
) : JsonRpcNotification() {
	/**
	 * A notification from the server to the client that a subscribed resource has been updated.
	 * The client may re-fetch the resource with `resources/read`.
	 *
	 * @property uri The URI of the resource that has been updated.
	 */
	@Serializable
	data class ResourceUpdatedParams(
		val uri: String,
		override val _meta: Map<String, JsonElement>? = null,
	) : NotificationParams
}
