package sh.ondr.mcp4k.schema.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.content.ToolContent
import sh.ondr.mcp4k.schema.core.Result

@Serializable
data class CallToolResult(
	val content: List<ToolContent> = emptyList(),
	val isError: Boolean? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
