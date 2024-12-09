package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.Serializable

@Serializable
data class Tool(
	val name: String,
	val description: String? = null,
	val inputSchema: ToolInputSchema,
)
