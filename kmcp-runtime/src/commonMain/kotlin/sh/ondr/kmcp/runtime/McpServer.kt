package sh.ondr.kmcp.runtime

import sh.ondr.kmcp.runtime.core.McpComponent
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.ServerCapabilities

// A simple server that just responds to "initialize" and expects "initialized" notification.
class McpServer(
	transport: Transport,
	basicRawLogger: ((String) -> Unit)? = null,
) : McpComponent(transport, basicRawLogger) {
	override suspend fun handleInitializeRequest(params: InitializeRequest.InitializeParams): InitializeResult {
		return InitializeResult(
			protocolVersion = "2024-11-05",
			capabilities = ServerCapabilities(),
			serverInfo = Implementation("TestServer", "1.0.0"),
		)
	}

	override suspend fun handleInitializedNotification() {
		// TODO - do something here
	}
}
