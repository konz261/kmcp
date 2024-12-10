package sh.ondr.kmcp.runtime

import sh.ondr.kmcp.runtime.tools.Tool
import sh.ondr.kmcp.runtime.tools.ToolHandler

object McpRegistry {
	val globalTools = mutableMapOf<String, Tool>()
	val globalToolHandlers = mutableMapOf<String, ToolHandler>()
}
