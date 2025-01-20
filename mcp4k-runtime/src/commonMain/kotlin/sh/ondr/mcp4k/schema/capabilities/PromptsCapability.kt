package sh.ondr.mcp4k.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class PromptsCapability(
	val listChanged: Boolean? = null,
)
