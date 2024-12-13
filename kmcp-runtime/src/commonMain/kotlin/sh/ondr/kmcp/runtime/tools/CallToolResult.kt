package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.content.ToolContent

@Serializable
data class CallToolResult(
	val content: List<ToolContent> = emptyList(),
	val isError: Boolean? = null,
)
