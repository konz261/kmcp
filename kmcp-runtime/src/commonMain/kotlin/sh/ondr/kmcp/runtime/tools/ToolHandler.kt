package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import sh.ondr.kmcp.runtime.kmcpJson
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.CallToolResult

interface ToolHandler {
	fun call(params: JsonObject): CallToolResult
}

typealias GenericToolHandler = TypedToolHandler<*>

data class TypedToolHandler<T : @Serializable Any>(
	val function: T.() -> ToolContent,
	val paramsSerializer: KSerializer<T>,
) : ToolHandler {
	@Suppress("UNCHECKED_CAST")
	override fun call(params: JsonObject): CallToolResult {
		val toolParams: Any = kmcpJson.decodeFromJsonElement(paramsSerializer, params)
		val toolContent = function(toolParams as T)
		return CallToolResult(
			content = listOf(toolContent),
		)
	}
}
