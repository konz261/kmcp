package sh.ondr.mcp4k.schema.sampling

import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.content.SamplingContent
import sh.ondr.mcp4k.schema.core.Role

@Serializable
data class SamplingMessage(
	val role: Role,
	val content: SamplingContent,
)
