package sh.ondr.kmcp.schema.sampling

import kotlinx.serialization.Serializable

@Serializable
data class ModelPreferences(
	val hints: List<ModelHint>? = null,
	val costPriority: Double? = null,
	val speedPriority: Double? = null,
	val intelligencePriority: Double? = null,
)
