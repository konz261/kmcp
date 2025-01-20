package sh.ondr.mcp4k.schema.sampling

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.content.SamplingContent
import sh.ondr.mcp4k.schema.core.Result
import sh.ondr.mcp4k.schema.core.Role

@Serializable
class CreateMessageResult(
	val content: SamplingContent,
	val model: String,
	val role: Role,
	val stopReason: String? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
