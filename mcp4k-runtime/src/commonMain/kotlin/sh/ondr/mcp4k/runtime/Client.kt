@file:OptIn(InternalSerializationApi::class)

package sh.ondr.mcp4k.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import sh.ondr.mcp4k.runtime.core.ClientApprovable
import sh.ondr.mcp4k.runtime.core.MCP_VERSION
import sh.ondr.mcp4k.runtime.core.pagination.PaginatedEndpoint
import sh.ondr.mcp4k.runtime.error.MethodNotFoundException
import sh.ondr.mcp4k.runtime.sampling.SamplingProvider
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.Transport
import sh.ondr.mcp4k.schema.capabilities.ClientCapabilities
import sh.ondr.mcp4k.schema.capabilities.Implementation
import sh.ondr.mcp4k.schema.capabilities.InitializeRequest
import sh.ondr.mcp4k.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.mcp4k.schema.capabilities.InitializeResult
import sh.ondr.mcp4k.schema.capabilities.InitializedNotification
import sh.ondr.mcp4k.schema.capabilities.RootsCapability
import sh.ondr.mcp4k.schema.core.JsonRpcRequest
import sh.ondr.mcp4k.schema.core.JsonRpcResponse
import sh.ondr.mcp4k.schema.core.PaginatedResult
import sh.ondr.mcp4k.schema.prompts.ListPromptsRequest
import sh.ondr.mcp4k.schema.resources.ListResourceTemplatesRequest
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest
import sh.ondr.mcp4k.schema.roots.ListRootsResult
import sh.ondr.mcp4k.schema.roots.Root
import sh.ondr.mcp4k.schema.roots.RootsListChangedNotification
import sh.ondr.mcp4k.schema.sampling.CreateMessageRequest.CreateMessageParams
import sh.ondr.mcp4k.schema.sampling.CreateMessageResult
import sh.ondr.mcp4k.schema.tools.ListToolsRequest
import sh.ondr.mcp4k.schema.tools.Tool
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
 *     .withClientInfo("MyCustomClient", "1.0.0")
 *     .withRoot(Root(uri = "file:///home/user/project", name = "My Project"))
 *     .withSamplingProvider { createMessageParams ->
 *     		// ...
 *     }
 *     .withTransport(StdioTransport()))
 *     .withTransportLogger(
 *       logIncoming = { msg -> println("CLIENT INCOMING: $msg") },
 *       logOutgoing = { msg -> println("CLIENT OUTGOING: $msg") },
 *     )
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
	private val logIncoming: suspend (String) -> Unit,
	private val logOutgoing: suspend (String) -> Unit,
	private val permissionCallback: suspend (ClientApprovable) -> Boolean,
	private val roots: MutableList<Root> = mutableListOf(),
	private val samplingProvider: SamplingProvider? = null,
	private val transport: Transport,
	coroutineContext: CoroutineContext,
) : McpComponent(
		transport = transport,
		logIncoming = logIncoming,
		logOutgoing = logOutgoing,
		coroutineContext = coroutineContext,
	) {
	private var initialized = false

	/**
	 * Initiates the MCP lifecycle by sending an `initialize` request with the specified
	 * client info and capabilities, and then sending an `initialized` notification once the server responds.
	 * Returns an [InitializeResult] containing information about the server.
	 *
	 * @throws IllegalStateException if initialization fails.
	 * @return [InitializeResult] containing information about the server.
	 */
	suspend fun initialize(): InitializeResult? {
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
		return response.result?.deserializeResult()
	}

	/**
	 * Checks if the client has completed the initialization phase.
	 */
	fun isInitialized(): Boolean = initialized

	/**
	 * Fetches all available tools from the server using pagination.
	 */
	suspend fun getAllTools(): List<Tool> {
		val allTools = mutableListOf<Tool>()

		// List available tools using pagination
		fetchPagesAsFlow(ListToolsRequest).collect { pageOfTools ->
			allTools += pageOfTools
		}
		return allTools
	}

	override suspend fun handleToolListChangedNotification() {
		val tools = getAllTools()
		onToolsChanged(tools)
	}

	/**
	 * Adds a [Root] to the client. If a root with the same name or uri already exists, it will not be added.
	 * @return `true` if the root was added, `false` otherwise.
	 */
	fun addRoot(root: Root): Boolean {
		val duplicateName: Boolean = roots.any { it.name == root.name }
		val duplicateUri: Boolean = roots.any { it.uri == root.uri }
		if (duplicateName || duplicateUri) return false
		roots.add(root)
		scope.launch {
			sendNotification(RootsListChangedNotification())
		}
		return true
	}

	/**
	 * Removes a [Root] from the client.
	 * @return `true` if the root was removed, `false` otherwise.
	 */
	fun removeRoot(root: Root): Boolean {
		val removed = roots.removeAll { it == root }
		if (removed) {
			scope.launch {
				sendNotification(RootsListChangedNotification())
			}
		}
		return removed
	}

	/**
	 * Removes a [Root] from the client.
	 * @param name The name of the root to remove.
	 * @return `true` if the root was removed, `false` otherwise.
	 */
	fun removeRootByName(name: String): Boolean {
		val removed = roots.removeAll { it.name == name }
		if (removed) {
			scope.launch {
				sendNotification(RootsListChangedNotification())
			}
		}
		return removed
	}

	/**
	 * Removes a [Root] from the client.
	 * @param uri The uri of the root to remove.
	 * @return `true` if the root was removed, `false` otherwise.
	 */
	fun removeRootByUri(uri: String): Boolean {
		val removed = roots.removeAll { it.uri == uri }
		if (removed) {
			scope.launch {
				sendNotification(RootsListChangedNotification())
			}
		}
		return removed
	}

	override suspend fun handleListRootsRequest(): ListRootsResult {
		if (clientCapabilities.roots == null) {
			// Should always be present, just in case we adhere to spec and respond with -32601 (Method not found)
			throw MethodNotFoundException("Client does not support roots")
		}

		return ListRootsResult(
			roots = roots.toList(),
		)
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

	/**
	 * Builder for creating a [Client] instance.
	 *
	 * Usage:
	 * ```
	 * val client = Client.Builder()
	 *     .withClientInfo("MyCustomClient", "1.0.0")
	 *     .withRoot(Root(uri = "file:///home/user/project", name = "My Project"))
	 *     .withSamplingProvider { createMessageParams ->
	 *     		// ...
	 *     }
	 *     .withTransport(StdioTransport()))
	 *     .withTransportLogger(
	 *       logIncoming = { msg -> println("CLIENT INCOMING: $msg") },
	 *       logOutgoing = { msg -> println("CLIENT OUTGOING: $msg") },
	 *     )
	 *     .build()
	 *
	 * client.start()
	 * client.initialize()
	 * ```
	 *
	 * Roots capabilities is enabled by default and returns an empty list.
	 */
	class Builder {
		private var builderCapabilities: ClientCapabilities = ClientCapabilities(roots = RootsCapability(listChanged = true))
		private var builderClientName: String = "MyClient"
		private var builderClientVersion: String = "1.0.0"
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var builderLogIncoming: suspend (String) -> Unit = {}
		private var builderLogOutgoing: suspend (String) -> Unit = {}
		private var builderPermissionCallback: suspend (ClientApprovable) -> Boolean = { true }
		private var builderRoots: MutableList<Root> = mutableListOf()
		private var builderSamplingProvider: SamplingProvider? = null
		private var builderTransport: Transport? = null
		private var used = false

		/**
		 * Sets the client's name and version returned during initialization.
		 * Defaults to "MyClient" and "1.0.0" if not set.
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
		 * Callback that should ask the user for permission to approve the given [ClientApprovable].
		 * The callback should return `true` if the user approves the request, `false` otherwise.
		 */
		fun withPermissionCallback(callback: suspend (ClientApprovable) -> Boolean) =
			apply {
				builderPermissionCallback = callback
			}

		/**
		 * Adds a [Root] the client.
		 */
		fun withRoot(root: Root) =
			apply {
				builderRoots.add(root)
			}

		/**
		 * Adds a list of [Root] instances to the client.
		 */
		fun withRoots(roots: List<Root>) =
			apply {
				builderRoots.addAll(roots)
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
		 * Sets separate loggers for incoming and outgoing transport messages.
		 *
		 * @param logIncoming Logger for incoming transport messages
		 * @param logOutgoing Logger for outgoing transport messages
		 */
		fun withTransportLogger(
			logIncoming: suspend (String) -> Unit = {},
			logOutgoing: suspend (String) -> Unit = {},
		) = apply {
			builderLogIncoming = logIncoming
			builderLogOutgoing = logOutgoing
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
				logIncoming = builderLogIncoming,
				logOutgoing = builderLogOutgoing,
				permissionCallback = builderPermissionCallback,
				roots = builderRoots,
				samplingProvider = builderSamplingProvider,
				transport = t,
			)
		}
	}
}
