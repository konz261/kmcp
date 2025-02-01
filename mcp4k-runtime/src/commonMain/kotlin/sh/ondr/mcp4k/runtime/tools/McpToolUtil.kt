@file:OptIn(InternalSerializationApi::class)

package sh.ondr.mcp4k.runtime.tools

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import sh.ondr.koja.Schema
import sh.ondr.koja.toSchema
import sh.ondr.mcp4k.runtime.core.mcpToolParams
import sh.ondr.mcp4k.schema.tools.Tool

fun getMcpTool(name: String): Tool {
	val params = mcpToolParams[name] ?: throw IllegalStateException("Tool not found: $name")
	val paramsSchema = params.serializer().descriptor.toSchema() as Schema.ObjectSchema
	return Tool(
		name = name,
		description = paramsSchema.description,
		inputSchema = paramsSchema.copy(description = null),
	)
}
