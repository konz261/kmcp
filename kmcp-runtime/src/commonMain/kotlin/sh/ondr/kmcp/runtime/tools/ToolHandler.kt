package sh.ondr.kmcp.runtime.tools

interface ToolHandler {
	fun invoke(arguments: Map<String, Any?>): Any?
}
