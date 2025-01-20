package sh.ondr.mcp4k.schema.completion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("completion/complete")
data class CompleteRequest(
	override val id: String,
	val params: CompleteParams,
) : JsonRpcRequest() {
	@Serializable
	data class CompleteParams(
		val ref: CompleteRef,
		val argument: Argument,
		val _meta: Map<String, JsonElement>? = null,
	) {
		@Serializable
		data class Argument(
			val name: String,
			val value: String,
		)
	}
}
