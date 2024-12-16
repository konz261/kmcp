package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.core.Result

@Serializable
data class CallToolResult(
	val content: List<ToolContent> = emptyList(),
	val isError: Boolean? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
