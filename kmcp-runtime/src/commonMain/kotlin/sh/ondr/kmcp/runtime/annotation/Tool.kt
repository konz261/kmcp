package sh.ondr.kmcp.runtime.annotation

@Target(AnnotationTarget.FUNCTION)
annotation class Tool(
	val description: String = "",
)
