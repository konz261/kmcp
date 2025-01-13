package sh.ondr.kmcp.runtime.annotation

@Target(AnnotationTarget.FUNCTION)
annotation class McpTool(
	val description: String = "",
)
