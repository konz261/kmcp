package sh.ondr.mcp4k.runtime.resources

data class File(
	val relativePath: String,
	val name: String? = null,
	val description: String? = null,
	val mimeType: String? = null,
)
