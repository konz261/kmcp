package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class ResourcesCapability(
	val subscribe: Boolean? = null,
	val listChanged: Boolean? = null,
)
