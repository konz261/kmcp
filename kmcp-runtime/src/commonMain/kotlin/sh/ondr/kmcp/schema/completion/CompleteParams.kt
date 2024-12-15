package sh.ondr.kmcp.schema.completion

import kotlinx.serialization.Serializable

@Serializable
data class CompleteParams(
	val ref: CompleteRef,
	val argument: Argument,
) {
	@Serializable
	data class Argument(
		val name: String,
		val value: String,
	)
}
