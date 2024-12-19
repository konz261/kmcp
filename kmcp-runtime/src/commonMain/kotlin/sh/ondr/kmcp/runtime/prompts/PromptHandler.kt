package sh.ondr.kmcp.runtime.prompts

import kotlinx.serialization.json.JsonObject
import sh.ondr.kmcp.schema.prompts.GetPromptResult

interface PromptHandler {
	fun call(params: JsonObject): GetPromptResult
}
