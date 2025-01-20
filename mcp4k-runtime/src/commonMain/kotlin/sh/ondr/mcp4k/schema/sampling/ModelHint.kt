package sh.ondr.mcp4k.schema.sampling

import kotlinx.serialization.Serializable

@Serializable
data class ModelHint(
	val name: String? = null,
)
