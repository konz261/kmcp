@file:OptIn(ExperimentalSerializationApi::class)

package sh.ondr.kmcp.schema.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.runtime.core.JSON_RPC_VERSION

/**
 * Base class for all JSON-RPC notifications.
 * Notifications do not have an ID and do not expect a response.
 *
 * All notifications share a `method` field. Polymorphic deserialization
 * is based on `method`.
 */
@Serializable
@Polymorphic
abstract class JsonRpcNotification : JsonRpcMessage {
	val jsonrpc: String = JSON_RPC_VERSION
	abstract val params: NotificationParams?
}

/**
 * Base parameters for a notification.
 * Notifications can have `_meta` and other fields depending on the subtype.
 */
interface NotificationParams {
	val _meta: Map<String, JsonElement>?
}

/**
 * This notification is sent by either side to indicate that it is cancelling a previously-issued request.
 *
 * The request SHOULD still be in-flight, but due to communication latency,
 * it is possible that this notification may arrive after the request has completed.
 * This indicates that the result will be unused, so any associated processing SHOULD stop.
 *
 * A client MUST NOT attempt to cancel its `initialize` request.
 *
 * @property requestId The ID of the request to cancel.
 * @property reason An optional string describing the reason for cancellation.
 */
@Serializable
@SerialName("notifications/cancelled")
data class CancelledNotification(
	override val params: CancelledParams,
) : JsonRpcNotification() {
	@Serializable
	data class CancelledParams(
		val requestId: String,
		val reason: String? = null,
		override val _meta: Map<String, JsonElement>? = null,
	) : NotificationParams
}
