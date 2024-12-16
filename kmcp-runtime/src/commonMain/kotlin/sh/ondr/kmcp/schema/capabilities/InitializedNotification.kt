package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.NotificationParams

/**
 * This notification is sent from the client to the server after initialization has finished.
 * This indicates that the client is now ready for normal operations.
 */
@Serializable
@SerialName("notifications/initialized")
data class InitializedNotification(
	override val method: String = "notifications/initialized",
	override val params: NotificationParams? = null,
) : JsonRpcNotification()
