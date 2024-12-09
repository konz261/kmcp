package sh.ondr.kmcp.runtime.base

import kotlinx.serialization.Serializable

@Serializable
data class Annotations(
	val audience: List<Role>? = null,
	val priority: Double? = null,
)
