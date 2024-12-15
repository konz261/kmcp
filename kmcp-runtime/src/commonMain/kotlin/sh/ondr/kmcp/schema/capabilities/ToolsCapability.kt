package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class ToolsCapability(
	val listChanged: Boolean? = null,
)
