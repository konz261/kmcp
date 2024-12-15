package sh.ondr.kmcp.schema.sampling

import kotlinx.serialization.Serializable

@Serializable
data class ModelHint(
	val name: String? = null,
)
