package sh.ondr.mcp4k.runtime.annotation

@Target(AnnotationTarget.FUNCTION)
annotation class McpTool(
	val description: String = "",
)
