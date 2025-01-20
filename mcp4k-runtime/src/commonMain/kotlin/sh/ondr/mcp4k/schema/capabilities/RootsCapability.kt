package sh.ondr.mcp4k.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class RootsCapability(
	val listChanged: Boolean? = null,
)
