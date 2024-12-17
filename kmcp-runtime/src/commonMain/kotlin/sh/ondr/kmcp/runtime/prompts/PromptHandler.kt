package sh.ondr.kmcp.runtime.prompts

import sh.ondr.kmcp.schema.prompts.GetPromptResult

class PromptHandler(
	val generate: (Map<String, String>?) -> GetPromptResult,
)
