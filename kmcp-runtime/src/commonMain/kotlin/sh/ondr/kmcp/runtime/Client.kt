package sh.ondr.kmcp.runtime

import kotlinx.coroutines.Dispatchers
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.ClientCapabilities
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest
import sh.ondr.kmcp.schema.capabilities.InitializedNotification
import sh.ondr.kmcp.schema.core.JsonRpcResponse
import kotlin.coroutines.CoroutineContext

class Client(
	transport: Transport,
	coroutineContext: CoroutineContext = Dispatchers.Default,
	basicRawLogger: ((String) -> Unit)? = null,
) : McpComponent(transport, basicRawLogger, coroutineContext) {
	private var initialized = false

	suspend fun initialize() {
		val response: JsonRpcResponse =
			sendRequest { id ->
				InitializeRequest(
					id = id,
					InitializeRequest.InitializeParams(
						protocolVersion = "2024-11-05",
						capabilities = ClientCapabilities(),
						clientInfo =
							Implementation(
								name = "TestClient",
								version = "1.0.0",
							),
					),
				)
			}

		if (response.error != null) {
			throw IllegalStateException("Initialization failed: ${response.error.message}")
		}

		val initNotification = InitializedNotification()
		sendNotification(initNotification)
		initialized = true
	}
}
