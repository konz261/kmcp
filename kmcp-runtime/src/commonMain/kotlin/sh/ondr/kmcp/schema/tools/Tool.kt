package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.Serializable
import sh.ondr.jsonschema.JsonSchema

@Serializable
data class Tool(
	val name: String,
	val description: String? = null,
	val inputSchema: JsonSchema,
)
