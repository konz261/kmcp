package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.runtime.kmcpJson
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.CallToolResult

typealias GenericToolHandler = ToolHandler<*>

data class ToolHandler<T : @Serializable Any>(
	val function: T.() -> ToolContent,
	val paramsSerializer: KSerializer<T>,
) {
	@Suppress("UNCHECKED_CAST")
	fun call(params: JsonElement): CallToolResult {
		val toolParams: Any = kmcpJson.decodeFromJsonElement(paramsSerializer, params)
		val toolContent = function(toolParams as T)
		return CallToolResult(
			content = listOf(toolContent),
		)
	}
}
