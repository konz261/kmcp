package sh.ondr.mcp4k.test.tools

import kotlinx.coroutines.delay
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.annotation.McpTool
import sh.ondr.mcp4k.runtime.core.toTextContent
import sh.ondr.mcp4k.schema.content.ToolContent

/**
 * Greets a user with an optional age
 * @param name The name of the user
 * @param age The age of the user (defaults to 25)
 */
@McpTool
suspend fun Server.greet(
	name: String,
	age: Int = 25,
): ToolContent {
	// Simulate some async work
	delay(10)
	return "Hello $name! You are $age years old.".toTextContent()
}

/**
 * Tool with no parameters
 */
@McpTool
fun noParamTool(): ToolContent = "No params needed!".toTextContent()

/**
 * Simulates a slow operation for testing cancellation
 * @param iterations Number of iterations to perform
 */
@McpTool
suspend fun slowToolOperation(iterations: Int = 10): ToolContent {
	for (i in 1..iterations) {
		// Allow cancellation between iterations
		delay(100)
	}
	return "Operation completed after $iterations iterations".toTextContent()
}
