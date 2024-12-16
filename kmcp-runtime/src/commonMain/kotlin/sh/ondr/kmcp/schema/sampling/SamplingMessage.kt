package sh.ondr.kmcp.schema.sampling

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.content.SamplingContent
import sh.ondr.kmcp.schema.core.Role

@Serializable
data class SamplingMessage(
	val role: Role,
	val content: SamplingContent,
)
