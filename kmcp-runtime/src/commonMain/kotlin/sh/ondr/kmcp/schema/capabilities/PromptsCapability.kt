package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class PromptsCapability(
	val listChanged: Boolean? = null,
)
