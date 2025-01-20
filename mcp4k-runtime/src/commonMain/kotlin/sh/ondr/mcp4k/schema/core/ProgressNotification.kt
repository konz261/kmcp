package sh.ondr.mcp4k.schema.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
@SerialName("notifications/progress")
data class ProgressNotification(
	override val params: ProgressParams,
) : JsonRpcNotification() {
	/**
	 * An out-of-band notification used to inform the receiver of a progress update
	 * for a long-running request.
	 *
	 * @property progressToken The progress token from the initial request. Token is string|number.
	 * @property progress The current progress made (increases each time). Using Double to represent numeric progress.
	 * @property total The total amount of progress required, if known.
	 */
	@Serializable
	data class ProgressParams(
		val progressToken: JsonPrimitive,
		val progress: Double,
		val total: Double? = null,
		override val _meta: Map<String, JsonElement>? = null,
	) : NotificationParams
}
