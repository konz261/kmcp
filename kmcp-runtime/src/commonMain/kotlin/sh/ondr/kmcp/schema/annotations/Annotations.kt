package sh.ondr.kmcp.schema.annotations

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.Role

@Serializable
data class Annotations(
	val audience: List<Role>? = null,
	val priority: Double? = null,
)
