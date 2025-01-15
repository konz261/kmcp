package sh.ondr.kmcp.runtime.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import sh.ondr.kmcp.runtime.core.mcpJson
import sh.ondr.kmcp.schema.core.JsonRpcMessage
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.Result
import sh.ondr.kmcp.schema.tools.ListToolsResult
import sh.ondr.koja.kojaJson

// TODO clean this mess up

/**
 * Serializes a result object into a JsonElement.
 */
inline fun <reified T : Result> serializeResult(value: T): JsonElement {
	return if (value is ListToolsResult) {
		// Workaround to serialize JsonSchema correctly
		kojaJson.encodeToJsonElement<ListToolsResult>(value)
	} else {
		mcpJson.encodeToJsonElement(value)
	}
}

inline fun <reified T : Result> JsonElement?.deserializeResult(): T? {
	if (this == null) return null

	return if (T::class == ListToolsResult::class) {
		// Decode using JsonSchema.json for ListToolsResult
		kojaJson.decodeFromJsonElement<ListToolsResult>(this) as T
	} else {
		// Decode using KMCP.json for all other result types
		mcpJson.decodeFromJsonElement<T>(this)
	}
}

inline fun <reified T : @Serializable Any> T.toJsonObject(): JsonObject {
	return mcpJson.encodeToJsonElement(this).jsonObject
}

fun String.toJsonRpcMessage(): JsonRpcMessage {
	return mcpJson.decodeFromString(JsonRpcMessageSerializer, this)
}

fun JsonRpcNotification.serializeToString(): String {
	return mcpJson.encodeToString(JsonRpcNotification.serializer(), this)
}

fun JsonRpcRequest.serializeToString(): String {
	return mcpJson.encodeToString(JsonRpcRequest.serializer(), this)
}
