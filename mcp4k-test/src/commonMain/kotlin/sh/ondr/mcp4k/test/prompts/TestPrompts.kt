package sh.ondr.mcp4k.test.prompts

import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.annotation.McpPrompt
import sh.ondr.mcp4k.runtime.annotation.McpTool
import sh.ondr.mcp4k.runtime.core.toTextContent
import sh.ondr.mcp4k.runtime.prompts.buildPrompt
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.content.ToolContent
import sh.ondr.mcp4k.schema.core.Role
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.schema.prompts.PromptMessage

/**
 * Code review prompt for Server extension function testing
 * @param code The code to review
 */
@McpPrompt
fun Server.codeReviewPrompt(code: String) =
	buildPrompt {
		user("Please review the following code:")
		user("```\n$code\n```")
	}

/**
 * Simple prompt for testing
 * @param code The code parameter
 */
@McpPrompt
fun secondPrompt(code: String) =
	buildPrompt {
		user("Second prompt with code: $code")
	}

/**
 * Third test prompt
 * @param code The code parameter
 */
@McpPrompt
fun thirdPrompt(code: String) =
	buildPrompt {
		user("Third prompt with code: $code")
	}

/**
 * Fourth test prompt
 * @param code The code parameter
 */
@McpPrompt
fun fourthPrompt(code: String) =
	buildPrompt {
		user("Fourth prompt with code: $code")
	}

/**
 * Fifth test prompt
 * @param code The code parameter
 */
@McpPrompt
fun fifthPrompt(code: String) =
	buildPrompt {
		user("Fifth prompt with code: $code")
	}

/**
 * Prompt that returns GetPromptResult directly (for error testing)
 * @param code The code to review
 */
@McpPrompt
fun strictReviewPrompt(code: String): GetPromptResult =
	GetPromptResult(
		messages = listOf(
			PromptMessage(role = Role.USER, content = TextContent("Please review: $code")),
		),
	)

/**
 * Simple code review prompt (from InitializationTest)
 * @param code The code to review
 */
@McpPrompt
fun simpleCodeReviewPrompt(code: String): GetPromptResult =
	buildPrompt {
		user("You are a code review assistant.")
		user("Please review the following code and provide feedback:")
		user("```\n$code\n```")
		assistant("I'll analyze this code and provide constructive feedback.")
	}

/**
 * Simple greeting tool (from InitializationTest)
 * Note: This is here temporarily as it was defined inline in a test
 * @param name The name to greet
 */
@McpTool
fun simpleGreet(name: String): ToolContent = "Hello, $name!".toTextContent()
