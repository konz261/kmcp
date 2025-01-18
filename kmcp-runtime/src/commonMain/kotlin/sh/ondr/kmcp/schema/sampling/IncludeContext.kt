package sh.ondr.kmcp.schema.sampling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A request to include context from one or more MCP servers, as requested by the caller (the server).
 * The client MAY ignore or override this request.
 */
@Serializable
enum class IncludeContext {
	@SerialName("allServers")
	ALL_SERVERS,

	@SerialName("none")
	NONE,

	@SerialName("thisServer")
	THIS_SERVER,
}
