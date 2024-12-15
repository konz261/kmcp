package sh.ondr.kmcp.schema.completion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.result.Result

@Serializable
data class CompleteResult(
	val completion: CompletionData,
	override val _meta: Map<String, JsonElement>? = null,
) : Result {
	@Serializable
	data class CompletionData(
		val values: List<String>,
		val total: Int? = null,
		val hasMore: Boolean? = null,
	)
}
