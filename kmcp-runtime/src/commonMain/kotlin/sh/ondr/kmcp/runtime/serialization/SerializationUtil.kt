package sh.ondr.kmcp.runtime.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import sh.ondr.kmcp.runtime.KMCP
import sh.ondr.kmcp.schema.core.JsonRpcMessage
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.JsonRpcResponse

inline fun <reified T : @Serializable Any> T.toJsonObject(): JsonObject {
	return KMCP.json.encodeToJsonElement(this).jsonObject
}

fun String.toJsonRpcMessage(): JsonRpcMessage {
	return KMCP.json.decodeFromString(JsonRpcMessageSerializer, this)
}

fun JsonRpcResponse.serializeToString(): String {
	return KMCP.json.encodeToString(JsonRpcResponse.serializer(), this)
}

fun JsonRpcNotification.serializeToString(): String {
	return KMCP.json.encodeToString(JsonRpcNotification.serializer(), this)
}

fun JsonRpcRequest.serializeToString(): String {
	return KMCP.json.encodeToString(JsonRpcRequest.serializer(), this)
}
