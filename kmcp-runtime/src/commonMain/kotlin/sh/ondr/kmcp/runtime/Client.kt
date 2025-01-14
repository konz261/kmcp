@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.InternalSerializationApi
import sh.ondr.kmcp.runtime.core.MCP_VERSION
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.ClientCapabilities
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest
import sh.ondr.kmcp.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.kmcp.schema.capabilities.InitializedNotification
import sh.ondr.kmcp.schema.core.JsonRpcResponse
import kotlin.coroutines.CoroutineContext

/**
 * A client implementation that communicates using the MCP protocol.
 *
 * The [Client] is responsible for connecting to an MCP server, performing initialization,
 * and then participating in the MCP lifecycle (sending requests, handling responses and notifications).
 *
 * Typical usage:
 * ```
 * val client = Client.Builder()
 *     .withTransport(myTransport)
 *     .withClientInfo("MyClient", "2.0.0")
 *     .withCapabilities(ClientCapabilities(...))
 *     .build()
 *
 * client.start()
 * client.initialize()
 * // Now the client can send requests and handle responses.
 * ```
 */
class Client private constructor(
	private val transport: Transport,
	private val clientName: String,
	private val clientVersion: String,
	private val clientCapabilities: ClientCapabilities,
	private val logger: suspend (String) -> Unit,
	coroutineContext: CoroutineContext,
) : McpComponent(
		transport = transport,
		logger = logger,
		coroutineContext = coroutineContext,
	) {
	private var initialized = false

	/**
	 * Initiates the MCP lifecycle by sending an `initialize` request with the specified
	 * client info and capabilities, and then sending an `initialized` notification once the server responds.
	 *
	 * @throws IllegalStateException if initialization fails.
	 */
	suspend fun initialize() {
		val response: JsonRpcResponse = sendRequest { id ->
			InitializeRequest(
				id = id,
				params =
					InitializeParams(
						protocolVersion = MCP_VERSION,
						capabilities = clientCapabilities,
						clientInfo =
							Implementation(
								name = clientName,
								version = clientVersion,
							),
					),
			)
		}

		if (response.error != null) {
			throw IllegalStateException("Initialization failed: ${response.error.message}")
		}

		sendNotification(InitializedNotification())
		initialized = true
	}

	/**
	 * Checks if the client has completed the initialization phase.
	 */
	fun isInitialized(): Boolean = initialized

	/**
	 * Builder for creating a [Client] instance.
	 *
	 * Usage:
	 * ```
	 * val client = Client.Builder()
	 *     .withTransport(myTransport)
	 *     .withClientInfo("MyCustomClient", "1.2.3")
	 *     .withCapabilities(ClientCapabilities(roots = RootsCapability(listChanged = true)))
	 *     .withLogger { line -> println(line) }
	 *     .build()
	 *
	 * client.start()
	 * client.initialize()
	 * ```
	 */
	class Builder {
		private var builderTransport: Transport? = null
		private var builderClientName: String = "TestClient"
		private var builderClientVersion: String = "1.0.0"
		private var builderCapabilities: ClientCapabilities = ClientCapabilities()
		private var builderLogger: suspend (String) -> Unit = {}
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var used = false

		/**
		 * Sets the [Transport] used by this client.
		 * This is mandatory and must be called before [build].
		 */
		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
			}

		/**
		 * Sets the client's name and version returned during initialization.
		 * Defaults to "TestClient" and "1.0.0" if not set.
		 */
		fun withClientInfo(
			name: String,
			version: String,
		) = apply {
			builderClientName = name
			builderClientVersion = version
		}

		/**
		 * Sets the client's capabilities to be advertised during initialization.
		 * Defaults to an empty [ClientCapabilities] if not set.
		 */
		fun withCapabilities(capabilities: ClientCapabilities) =
			apply {
				builderCapabilities = capabilities
			}

		/**
		 * Adds a logger for incoming/outgoing messages.
		 */
		fun withLogger(logger: suspend (String) -> Unit) =
			apply {
				builderLogger = logger
			}

		/**
		 * Sets a coroutine dispatcher or context for the client's internal coroutines.
		 * Defaults to [Dispatchers.Default].
		 */
		fun withDispatcher(dispatcher: CoroutineContext) =
			apply {
				builderDispatcher = dispatcher
			}

		/**
		 * Builds the [Client] instance.
		 * @throws IllegalStateException if transport was not set
		 * @throws IllegalStateException if this builder is reused after building
		 */
		fun build(): Client {
			check(!used) { "This Builder has already been used." }
			used = true

			val t = builderTransport ?: error("Transport must be set via withTransport before building.")

			return Client(
				transport = t,
				clientName = builderClientName,
				clientVersion = builderClientVersion,
				clientCapabilities = builderCapabilities,
				logger = builderLogger,
				coroutineContext = builderDispatcher,
			)
		}
	}
}
