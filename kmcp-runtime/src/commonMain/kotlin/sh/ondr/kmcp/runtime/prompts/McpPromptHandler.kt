package sh.ondr.kmcp.runtime.prompts

import kotlinx.serialization.json.JsonObject
import sh.ondr.kmcp.schema.prompts.GetPromptResult

interface McpPromptHandler {
	suspend fun call(params: JsonObject): GetPromptResult
}
