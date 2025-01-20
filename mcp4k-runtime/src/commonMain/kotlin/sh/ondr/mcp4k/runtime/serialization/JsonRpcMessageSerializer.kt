package sh.ondr.mcp4k.runtime.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import sh.ondr.mcp4k.schema.core.JsonRpcMessage
import sh.ondr.mcp4k.schema.core.JsonRpcNotification
import sh.ondr.mcp4k.schema.core.JsonRpcRequest
import sh.ondr.mcp4k.schema.core.JsonRpcResponse

object JsonRpcMessageSerializer : JsonContentPolymorphicSerializer<JsonRpcMessage>(JsonRpcMessage::class) {
	override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JsonRpcMessage> {
		val obj = element.jsonObject

		val id = obj["id"]
		val method = obj["method"]
		val result = obj["result"]
		val error = obj["error"]

		return when {
			// Response: has id, and either result or error, but no method
			id != null && (result != null || error != null) && method == null -> JsonRpcResponse.serializer()

			// Request: has id and method
			id != null && method != null -> JsonRpcRequest.serializer()

			// Notification: has method but no id
			id == null && method != null -> JsonRpcNotification.serializer()
			else -> throw SerializationException("Unknown message type: $element")
		}
	}
}
