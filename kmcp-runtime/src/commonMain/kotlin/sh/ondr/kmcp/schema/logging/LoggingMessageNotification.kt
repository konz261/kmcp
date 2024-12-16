package sh.ondr.kmcp.schema.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.NotificationParams

@Serializable
@SerialName("notifications/message")
data class LoggingMessageNotification(
	override val params: LoggingMessageParams,
) : JsonRpcNotification() {
	/**
	 * A notification of a log message passed from server to client.
	 * If no `logging/setLevel` request has been sent, the server MAY decide which messages to send.
	 *
	 * @property level The severity of the log message.
	 * @property logger An optional name of the logger issuing this message.
	 * @property data The data to log, any JSON-serializable type.
	 */
	@Serializable
	data class LoggingMessageParams(
		val level: LoggingLevel,
		val logger: String? = null,
		val data: JsonElement,
		override val _meta: Map<String, JsonElement>? = null,
	) : NotificationParams
}
