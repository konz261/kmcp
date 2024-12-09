package sh.ondr.kmcp.runtime.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ToolArg(
	val description: String = "",
	val name: String = "",
)
