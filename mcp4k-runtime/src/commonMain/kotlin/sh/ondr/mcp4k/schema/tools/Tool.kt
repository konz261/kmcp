package sh.ondr.mcp4k.schema.tools

import kotlinx.serialization.Serializable
import sh.ondr.koja.Schema

@Serializable
data class Tool(
	val name: String,
	val description: String? = null,
	val inputSchema: Schema,
)
