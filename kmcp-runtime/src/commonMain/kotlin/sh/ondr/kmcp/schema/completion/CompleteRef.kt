package sh.ondr.kmcp.schema.completion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CompleteRef {
	@Serializable
	@SerialName("ref/prompt")
	data class PromptRef(
		val name: String,
	) : CompleteRef

	@Serializable
	@SerialName("ref/resource")
	data class ResourceRef(
		val uri: String,
	) : CompleteRef
}
