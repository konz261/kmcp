package sh.ondr.mcp4k.test.tools

import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.ServerContext
import sh.ondr.mcp4k.runtime.annotation.McpTool
import sh.ondr.mcp4k.runtime.core.toTextContent
import sh.ondr.mcp4k.schema.content.ToolContent

/**
 * Test context interface for storing values
 */
interface RemoteService : ServerContext {
	var value: String
}

/**
 * Implementation of the test context
 */
class RemoteServiceImpl : RemoteService {
	override var value: String = "Initial value"
}

/**
 * Stores a value in the server context
 * @param newValue The new value to store
 */
@McpTool
fun Server.storeValueInContext(newValue: String): ToolContent {
	val context = getContextAs<RemoteService>()
	val oldValue = context.value
	context.value = newValue
	return "Value updated from '$oldValue' to '$newValue'".toTextContent()
}
