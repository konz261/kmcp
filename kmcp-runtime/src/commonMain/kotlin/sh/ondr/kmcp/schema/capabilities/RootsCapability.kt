package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class RootsCapability(
	val listChanged: Boolean? = null,
)
