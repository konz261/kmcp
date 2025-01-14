package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.json.JsonObject
import sh.ondr.kmcp.schema.tools.CallToolResult

interface McpToolHandler {
	suspend fun call(params: JsonObject): CallToolResult
}
