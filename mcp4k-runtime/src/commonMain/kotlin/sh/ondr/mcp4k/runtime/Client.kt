@file:OptIn(InternalSerializationApi::class)

package sh.ondr.mcp4k.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.InternalSerializationApi
import sh.ondr.mcp4k.runtime.core.ClientApprovable
import sh.ondr.mcp4k.runtime.core.MCP_VERSION
import sh.ondr.mcp4k.runtime.core.pagination.PaginatedEndpoint
import sh.ondr.mcp4k.runtime.sampling.SamplingProvider
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.Transport
import sh.ondr.mcp4k.schema.capabilities.ClientCapabilities
import sh.ondr.mcp4k.schema.capabilities.Implementation
import sh.ondr.mcp4k.schema.capabilities.InitializeRequest
import sh.ondr.mcp4k.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.mcp4k.schema.capabilities.InitializedNotification
import sh.ondr.mcp4k.schema.core.JsonRpcRequest
import sh.ondr.mcp4k.schema.core.JsonRpcResponse
import sh.ondr.mcp4k.schema.core.PaginatedResult
import sh.ondr.mcp4k.schema.prompts.ListPromptsRequest
import sh.ondr.mcp4k.schema.resources.ListResourceTemplatesRequest
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest
import sh.ondr.mcp4k.schema.sampling.CreateMessageRequest.CreateMessageParams
import sh.ondr.mcp4k.schema.sampling.CreateMessageResult
import sh.ondr.mcp4k.schema.tools.ListToolsRequest
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
	private val clientCapabilities: ClientCapabilities,
	private val clientName: String,
	private val clientVersion: String,
	private val logger: suspend (String) -> Unit,
	private val permissionCallback: suspend (ClientApprovable) -> Boolean,
	private val samplingProvider: SamplingProvider? = null,
	private val transport: Transport,
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
	 * Repeatedly calls a paginated [endpoint], emitting one set of [ITEMS] per page
	 * until [PaginatedResult.nextCursor] is `null` or an error is thrown.
	 *
	 * **Usage**:
	 * ```
	 * // Example: Fetch all prompt pages
	 * val allPrompts = mutableListOf<Prompt>()
	 * client.fetchPagesAsFlow(ListPromptsRequest)
	 *     .collect { prompts ->
	 *         println("Received page with ${prompts.size} prompts")
	 *         allPrompts += prompts
	 *     }
	 * ```
	 *
	 * In practice, [endpoint] can be one of the following:
	 * - [ListPromptsRequest]
	 * - [ListToolsRequest]
	 * - [ListResourcesRequest]
	 * - [ListResourceTemplatesRequest]
	 *
	 * @param endpoint A [PaginatedEndpoint] describing how to build requests and transform results.
	 * @return A [Flow] that emits one [ITEMS] instance per page.
	 */
	inline fun <REQ : JsonRpcRequest, reified RES : PaginatedResult, ITEMS> fetchPagesAsFlow(
		endpoint: PaginatedEndpoint<REQ, RES, ITEMS>,
	): Flow<ITEMS> =
		flow {
			var cursor: String? = null
			do {
				val response = sendRequest { realId ->
					endpoint.requestFactory.create(realId, cursor)
				}

				response.error?.let { error ->
					throw IllegalStateException(
						"Server error (code=${error.code}): ${error.message}",
					)
				}
				val result = response.result
					?.deserializeResult<RES>()
					?: error("Null or invalid result from server.")

				val items = endpoint.transform(result)
				emit(items)

				cursor = result.nextCursor
			} while (cursor != null)
		}

	override suspend fun handleCreateMessageRequest(params: CreateMessageParams): CreateMessageResult {
		val approved = permissionCallback(params)
		if (!approved) {
			throw RuntimeException("User rejected sampling request")
		}

		if (samplingProvider == null) {
			throw IllegalStateException("Sampling not supported")
		}

		samplingProvider.createMessage(params).let { result ->
			// TODO: Handle logging, etc.
			return result
		}
	}

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
		private var builderCapabilities: ClientCapabilities = ClientCapabilities()
		private var builderPermissionCallback: suspend (ClientApprovable) -> Boolean = { true }
		private var builderClientName: String = "TestClient"
		private var builderClientVersion: String = "1.0.0"
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var builderLogger: suspend (String) -> Unit = {}
		private var builderSamplingProvider: SamplingProvider? = null
		private var builderTransport: Transport? = null
		private var used = false

		/**
		 * Sets the client's capabilities to be advertised during initialization.
		 * Defaults to an empty [ClientCapabilities] if not set.
		 */
		fun withCapabilities(capabilities: ClientCapabilities) =
			apply {
				builderCapabilities = capabilities
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
		 * Sets a coroutine dispatcher or context for the client's internal coroutines.
		 * Defaults to [Dispatchers.Default].
		 */
		fun withDispatcher(dispatcher: CoroutineContext) =
			apply {
				builderDispatcher = dispatcher
			}

		/**
		 * Adds a logger for incoming/outgoing messages.
		 */
		fun withLogger(logger: suspend (String) -> Unit) =
			apply {
				builderLogger = logger
			}

		/**
		 * Callback that should ask the user for permission to approve the given [ClientApprovable].
		 * The callback should return `true` if the user approves the request, `false` otherwise.
		 */
		fun withPermissionCallback(callback: suspend (ClientApprovable) -> Boolean) =
			apply {
				builderPermissionCallback = callback
			}

		/**
		 * Adds a [SamplingProvider] to the client's capabilities.
		 */
		fun withSamplingProvider(provider: SamplingProvider) =
			apply {
				builderCapabilities = builderCapabilities.copy(sampling = mapOf())
				require(builderSamplingProvider == null) { "Sampling provider already set" }
				builderSamplingProvider = provider
			}

		/**
		 * Sets the [Transport] used by this client.
		 * This is mandatory and must be called before [build].
		 */
		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
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
				clientCapabilities = builderCapabilities,
				clientName = builderClientName,
				clientVersion = builderClientVersion,
				coroutineContext = builderDispatcher,
				logger = builderLogger,
				permissionCallback = builderPermissionCallback,
				samplingProvider = builderSamplingProvider,
				transport = t,
			)
		}
	}
}
