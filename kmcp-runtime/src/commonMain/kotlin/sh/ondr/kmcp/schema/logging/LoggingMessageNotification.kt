package sh.ondr.kmcp.schema.logging

import kotlinx.serialization.Serializable

@Serializable
data class LoggingMessageNotificationParams(
	val level: LoggingLevel,
	val logger: String? = null,
	val data: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class LoggingMessageNotification(
	val method: String = "notifications/message",
	val params: LoggingMessageNotificationParams,
)
