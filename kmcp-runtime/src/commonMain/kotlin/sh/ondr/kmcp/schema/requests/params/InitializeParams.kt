package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.capabilities.ClientCapabilities
import sh.ondr.kmcp.schema.capabilities.Implementation

@Serializable
data class InitializeParams(
	val protocolVersion: String,
	val capabilities: ClientCapabilities,
	val clientInfo: Implementation,
)
