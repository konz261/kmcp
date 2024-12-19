package sh.ondr.kmcp.schema.sampling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("sampling/createMessage")
data class CreateMessageRequest(
	override val id: String,
	val params: CreateMessageParams,
) : JsonRpcRequest() {
	// includeContext = "allServers" | "none" | "thisServer"
	@Serializable
	data class CreateMessageParams(
		val messages: List<SamplingMessage>,
		val modelPreferences: ModelPreferences? = null,
		val systemPrompt: String? = null,
		val includeContext: String? = null,
		val temperature: Double? = null,
		val maxTokens: Int,
		val stopSequences: List<String>? = null,
		val metadata: Map<String, JsonElement>? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
