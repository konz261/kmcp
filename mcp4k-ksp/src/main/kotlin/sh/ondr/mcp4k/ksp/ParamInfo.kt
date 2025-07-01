package sh.ondr.mcp4k.ksp

data class ParamInfo(
	val name: String,
	val fqnType: String,
	val fqnTypeNonNullable: String,
	val readableType: String,
	val isNullable: Boolean,
	val hasDefault: Boolean,
	val isRequired: Boolean,
	var description: String? = null,
)
