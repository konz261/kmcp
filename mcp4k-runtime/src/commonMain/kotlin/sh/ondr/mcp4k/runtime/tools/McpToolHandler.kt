package sh.ondr.mcp4k.runtime.tools

import kotlinx.serialization.json.JsonObject
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.schema.tools.CallToolResult

interface McpToolHandler {
	suspend fun call(
		server: Server,
		params: JsonObject,
	): CallToolResult
}
