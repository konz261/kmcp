package sh.ondr.mcp4k.runtime.prompts

import kotlinx.serialization.json.JsonObject
import sh.ondr.mcp4k.schema.prompts.GetPromptResult

interface McpPromptHandler {
	suspend fun call(params: JsonObject): GetPromptResult
}
