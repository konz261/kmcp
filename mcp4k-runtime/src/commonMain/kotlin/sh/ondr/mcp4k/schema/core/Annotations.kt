package sh.ondr.mcp4k.schema.core

import kotlinx.serialization.Serializable

@Serializable
data class Annotations(
	val audience: List<Role>? = null,
	val priority: Double? = null,
)
