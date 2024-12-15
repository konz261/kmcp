import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.Role
import sh.ondr.kmcp.schema.content.SamplingContent
import sh.ondr.kmcp.schema.result.Result

@Serializable
class CreateMessageResult(
	val content: SamplingContent,
	val model: String,
	val role: Role,
	val stopReason: String? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
