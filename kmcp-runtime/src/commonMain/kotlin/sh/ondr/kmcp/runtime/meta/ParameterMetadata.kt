package sh.ondr.kmcp.runtime.meta

data class ParameterMetadata(
	val name: String,
	val type: String,
	val description: String?,
	val isOptional: Boolean,
)
