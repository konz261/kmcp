package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.sampling.ModelPreferences
import sh.ondr.kmcp.schema.sampling.SamplingMessage

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
)
