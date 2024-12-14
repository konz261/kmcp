package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import sh.ondr.kmcp.runtime.KMCP
import sh.ondr.kmcp.schema.content.ToolContent

typealias GenericToolHandler = ToolHandler<*, *>

data class ToolHandler<T : @Serializable Any, R : ToolContent>(
	val function: T.() -> R,
	val paramsSerializer: KSerializer<T>,
) {
	@Suppress("UNCHECKED_CAST")
	fun call(params: JsonElement?): CallToolResult {
		val toolParams: Any = KMCP.json.decodeFromJsonElement(paramsSerializer, params ?: buildJsonObject { })
		val toolContent = function(toolParams as T)
		return CallToolResult(
			content = listOf(toolContent),
		)
	}
}
