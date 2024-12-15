package sh.ondr.kmcp.schema.sampling

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.Role
import sh.ondr.kmcp.schema.content.SamplingContent

@Serializable
data class SamplingMessage(
	val role: Role,
	val content: SamplingContent,
)
