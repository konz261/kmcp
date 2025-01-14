package sh.ondr.kmcp.runtime

import CreateMessageResult
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sh.ondr.kmcp.runtime.core.mcpJson
import sh.ondr.kmcp.runtime.error.MethodNotFoundException
import sh.ondr.kmcp.runtime.error.MissingRequiredArgumentException
import sh.ondr.kmcp.runtime.error.ResourceNotFoundException
import sh.ondr.kmcp.runtime.error.UnknownArgumentException
import sh.ondr.kmcp.runtime.error.determineErrorResponse
import sh.ondr.kmcp.runtime.serialization.serializeResult
import sh.ondr.kmcp.runtime.serialization.serializeToString
import sh.ondr.kmcp.runtime.serialization.toJsonRpcMessage
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.runtime.transport.runTransportLoop
import sh.ondr.kmcp.schema.capabilities.InitializeRequest
import sh.ondr.kmcp.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.InitializedNotification
import sh.ondr.kmcp.schema.completion.CompleteRequest
import sh.ondr.kmcp.schema.completion.CompleteRequest.CompleteParams
import sh.ondr.kmcp.schema.completion.CompleteResult
import sh.ondr.kmcp.schema.core.CancelledNotification
import sh.ondr.kmcp.schema.core.CancelledNotification.CancelledParams
import sh.ondr.kmcp.schema.core.EmptyResult
import sh.ondr.kmcp.schema.core.JsonRpcError
import sh.ondr.kmcp.schema.core.JsonRpcErrorCodes
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.JsonRpcResponse
import sh.ondr.kmcp.schema.core.PingRequest
import sh.ondr.kmcp.schema.core.PingRequest.PingParams
import sh.ondr.kmcp.schema.core.ProgressNotification
import sh.ondr.kmcp.schema.core.ProgressNotification.ProgressParams
import sh.ondr.kmcp.schema.logging.LoggingMessageNotification
import sh.ondr.kmcp.schema.logging.LoggingMessageNotification.LoggingMessageParams
import sh.ondr.kmcp.schema.logging.SetLoggingLevelRequest
import sh.ondr.kmcp.schema.logging.SetLoggingLevelRequest.SetLoggingLevelParams
import sh.ondr.kmcp.schema.prompts.GetPromptRequest
import sh.ondr.kmcp.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest.ListPromptsParams
import sh.ondr.kmcp.schema.prompts.ListPromptsResult
import sh.ondr.kmcp.schema.prompts.PromptListChangedNotification
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesRequest
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesRequest.ListResourceTemplatesParams
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesResult
import sh.ondr.kmcp.schema.resources.ListResourcesRequest
import sh.ondr.kmcp.schema.resources.ListResourcesRequest.ListResourcesParams
import sh.ondr.kmcp.schema.resources.ListResourcesResult
import sh.ondr.kmcp.schema.resources.ReadResourceRequest
import sh.ondr.kmcp.schema.resources.ReadResourceRequest.ReadResourceParams
import sh.ondr.kmcp.schema.resources.ReadResourceResult
import sh.ondr.kmcp.schema.resources.ResourceListChangedNotification
import sh.ondr.kmcp.schema.resources.ResourceUpdatedNotification
import sh.ondr.kmcp.schema.resources.ResourceUpdatedNotification.ResourceUpdatedParams
import sh.ondr.kmcp.schema.resources.SubscribeRequest
import sh.ondr.kmcp.schema.resources.SubscribeRequest.SubscribeParams
import sh.ondr.kmcp.schema.resources.UnsubscribeRequest
import sh.ondr.kmcp.schema.resources.UnsubscribeRequest.UnsubscribeParams
import sh.ondr.kmcp.schema.roots.ListRootsRequest
import sh.ondr.kmcp.schema.roots.ListRootsResult
import sh.ondr.kmcp.schema.roots.RootsListChangedNotification
import sh.ondr.kmcp.schema.sampling.CreateMessageRequest
import sh.ondr.kmcp.schema.sampling.CreateMessageRequest.CreateMessageParams
import sh.ondr.kmcp.schema.tools.CallToolRequest
import sh.ondr.kmcp.schema.tools.CallToolRequest.CallToolParams
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.ListToolsRequest
import sh.ondr.kmcp.schema.tools.ListToolsRequest.ListToolsParams
import sh.ondr.kmcp.schema.tools.ListToolsResult
import sh.ondr.kmcp.schema.tools.ToolListChangedNotification
import kotlin.coroutines.CoroutineContext

/**
 * Base component for MCP clients/servers.
 * Handles request/response & notification routing, serialization, and connection lifecycle.
 */
abstract class McpComponent(
	private val transport: Transport,
	private val logger: (suspend (String) -> Unit)? = null,
	coroutineContext: CoroutineContext = Dispatchers.Default,
) {
	/**
	 * A supervisor scope used for launching coroutines that handle requests.
	 * If one request fails, it doesn't tear down the entire component.
	 */
	private val scope = CoroutineScope(coroutineContext + SupervisorJob())

	/**
	 * Tracks outgoing requests that we initiated, keyed by request ID.
	 * The [CompletableDeferred] is completed when a corresponding response arrives.
	 */
	private val outgoingRequests = mutableMapOf<String, CompletableDeferred<JsonRpcResponse>>()
	private val outgoingRequestsMutex = Mutex()

	/**
	 * Tracks incoming requests that we are currently processing, keyed by request ID.
	 * Each request is handled in a coroutine [Job]. This allows us to cancel a long-running
	 * operation if a `notifications/cancelled` is received.
	 */
	private val incomingRequests = mutableMapOf<String, Job>()
	private val incomingRequestsMutex = Mutex()

	private val nextRequestId = atomic(1L)

	// -----------------------------------------------------
	// Public lifecycle methods
	// -----------------------------------------------------

	/**
	 * Starts reading/writing from the given transport.
	 */
	suspend fun start() {
		transport.connect()
		scope.launch {
			runTransportLoop(
				transport = transport,
				onMessageLine = { onMessageLine(it) },
				onError = { onTransportError(it) },
				onClose = { onTransportClose() },
			)
		}
	}

	/**
	 * Sends a request and suspends until a corresponding response is received or this coroutine is cancelled.
	 *
	 * If the coroutine calling [sendRequest] is cancelled, a `notifications/cancelled`
	 * is automatically sent to the remote, so the remote side can stop processing.
	 */
	suspend fun sendRequest(builder: (id: String) -> JsonRpcRequest): JsonRpcResponse {
		val requestId = nextRequestId.getAndIncrement().toString()
		val deferred = CompletableDeferred<JsonRpcResponse>()
		outgoingRequestsMutex.withLock {
			outgoingRequests[requestId] = deferred
		}

		val request = builder(requestId)
		val serialized = request.serializeToString()
		logOutgoing(serialized)
		transport.writeString(serialized)

		// Await the response, or handle local cancellation
		try {
			return deferred.await()
		} catch (ce: CancellationException) {
			// Coroutine was cancelled. Remove from map and notify the remote side.
			outgoingRequestsMutex.withLock {
				outgoingRequests.remove(requestId)
			}
			val reason = ce.message?.ifBlank { null } ?: "User canceled"
			val notification = CancelledNotification(
				CancelledParams(
					requestId = requestId,
					reason = reason,
				),
			)
			sendNotification(notification)
			throw ce
		}
	}

	/**
	 * Sends a notification (fire-and-forget).
	 */
	suspend fun sendNotification(notification: JsonRpcNotification) {
		val serialized = notification.serializeToString()
		logOutgoing(serialized)
		transport.writeString(serialized)
	}

	/**
	 * Called when the transport closes.
	 * Subclasses may override to handle cleanup.
	 */
	protected open suspend fun onTransportClose() {}

	/**
	 * Called when the transport reports an error.
	 * By default, completes all outgoing requests exceptionally, but does not shut down the entire scope.
	 */
	protected open suspend fun onTransportError(error: Throwable) {
		completeAllOutgoingRequestsExceptionally(error)
	}

	// -----------------------------------------------------
	// Abstract Handlers for Requests (To be Overridden)
	// -----------------------------------------------------

	// Default implementations or notImplemented() placeholders
	open suspend fun handlePingRequest(params: PingParams?): EmptyResult = EmptyResult()

	open suspend fun handleInitializeRequest(params: InitializeParams): InitializeResult = notImplemented()

	open suspend fun handleCallToolRequest(params: CallToolParams): CallToolResult = notImplemented()

	open suspend fun handleListToolsRequest(params: ListToolsParams?): ListToolsResult = notImplemented()

	open suspend fun handleCreateMessageRequest(params: CreateMessageParams): CreateMessageResult = notImplemented()

	open suspend fun handleListRootsRequest(): ListRootsResult = notImplemented()

	open suspend fun handleListResourceTemplatesRequest(params: ListResourceTemplatesParams?): ListResourceTemplatesResult = notImplemented()

	open suspend fun handleListResourcesRequest(params: ListResourcesParams?): ListResourcesResult = notImplemented()

	open suspend fun handleReadResourceRequest(params: ReadResourceParams): ReadResourceResult = notImplemented()

	open suspend fun handleSubscribeRequest(params: SubscribeParams): EmptyResult = notImplemented()

	open suspend fun handleUnsubscribeRequest(params: UnsubscribeParams): EmptyResult = notImplemented()

	open suspend fun handleGetPromptRequest(params: GetPromptParams): GetPromptResult = notImplemented()

	open suspend fun handleListPromptsRequest(params: ListPromptsParams?): ListPromptsResult = notImplemented()

	open suspend fun handleSetLoggingLevelRequest(params: SetLoggingLevelParams): EmptyResult = notImplemented()

	open suspend fun handleCompleteRequest(params: CompleteParams): CompleteResult = notImplemented()

	// -----------------------------------------------------
	// Abstract Handlers for Notifications (To be Overridden)
	// -----------------------------------------------------

	/**
	 * Called when the remote side sends `notifications/initialized`.
	 */
	protected open suspend fun handleInitializedNotification(): Unit = notImplemented()

	/**
	 * Called when the remote side wants to cancel an in-progress request (by ID).
	 * The default implementation cancels the corresponding job in [incomingRequests].
	 */
	protected open suspend fun handleCancelledNotification(params: CancelledParams) {
		incomingRequestsMutex.withLock {
			val job = incomingRequests[params.requestId]
			if (job != null && job.isActive) {
				job.cancel(CancellationException("Remote side cancelled request: ${params.reason}"))
			}
		}
	}

	protected open suspend fun handleProgressNotification(params: ProgressParams): Unit = notImplemented()

	protected open suspend fun handleLoggingMessageNotification(params: LoggingMessageParams): Unit = notImplemented()

	protected open suspend fun handlePromptListChangedNotification(): Unit = notImplemented()

	protected open suspend fun handleResourceListChangedNotification(): Unit = notImplemented()

	protected open suspend fun handleResourceUpdatedNotification(params: ResourceUpdatedParams): Unit = notImplemented()

	protected open suspend fun handleRootsListChangedNotification(): Unit = notImplemented()

	protected open suspend fun handleToolListChangedNotification(): Unit = notImplemented()

	// -----------------------------------------------------
	// Internal Request & Notification Handling
	// -----------------------------------------------------

	/**
	 * Handles incoming requests by calling the appropriate typed handler and serializing the result to JSON.
	 */
	protected suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse =
		try {
			val jsonResult: JsonElement? = when (request) {
				is InitializeRequest -> serializeResult(handleInitializeRequest(request.params))
				is PingRequest -> serializeResult(handlePingRequest(request.params))
				is CallToolRequest -> serializeResult(handleCallToolRequest(request.params))
				is ListToolsRequest -> serializeResult(handleListToolsRequest(request.params))
				is CreateMessageRequest -> serializeResult(handleCreateMessageRequest(request.params))
				is ListRootsRequest -> serializeResult(handleListRootsRequest())
				is ListResourceTemplatesRequest -> serializeResult(handleListResourceTemplatesRequest(request.params))
				is ListResourcesRequest -> serializeResult(handleListResourcesRequest(request.params))
				is ReadResourceRequest -> serializeResult(handleReadResourceRequest(request.params))
				is SubscribeRequest -> serializeResult(handleSubscribeRequest(request.params))
				is UnsubscribeRequest -> serializeResult(handleUnsubscribeRequest(request.params))
				is GetPromptRequest -> serializeResult(handleGetPromptRequest(request.params))
				is ListPromptsRequest -> serializeResult(handleListPromptsRequest(request.params))
				is SetLoggingLevelRequest -> serializeResult(handleSetLoggingLevelRequest(request.params))
				is CompleteRequest -> serializeResult(handleCompleteRequest(request.params))
				else -> null // Unknown request => method not found
			}

			if (jsonResult == null) {
				request.returnErrorResponse("Method not found", JsonRpcErrorCodes.METHOD_NOT_FOUND)
			} else {
				JsonRpcResponse(id = request.id, result = jsonResult)
			}
		} catch (e: ResourceNotFoundException) {
			request.returnErrorResponse(e.message ?: "Resource not found.", JsonRpcErrorCodes.RESOURCE_NOT_FOUND)
		} catch (e: MethodNotFoundException) {
			request.returnErrorResponse(e.message ?: "Method not found", JsonRpcErrorCodes.METHOD_NOT_FOUND)
		} catch (e: MissingRequiredArgumentException) {
			request.returnErrorResponse(e.message ?: "Invalid parameters.", JsonRpcErrorCodes.INVALID_PARAMS)
		} catch (e: UnknownArgumentException) {
			request.returnErrorResponse(e.message ?: "Invalid parameters.", JsonRpcErrorCodes.INVALID_PARAMS)
		} catch (e: IllegalArgumentException) {
			request.returnErrorResponse("Invalid params: ${e.message}", JsonRpcErrorCodes.INVALID_PARAMS)
		} catch (e: NotImplementedError) {
			request.returnErrorResponse("Method not found", JsonRpcErrorCodes.METHOD_NOT_FOUND)
		} catch (e: Throwable) {
			logError("Internal error while handling request $request", e)
			request.returnErrorResponse("Internal error: ${e.message ?: "unknown"}", JsonRpcErrorCodes.INTERNAL_ERROR)
		}

	/**
	 * Routes notifications to the appropriate typed handler.
	 */
	protected suspend fun handleNotification(notification: JsonRpcNotification) {
		try {
			when (notification) {
				is InitializedNotification -> handleInitializedNotification()
				is CancelledNotification -> handleCancelledNotification(notification.params)
				is ProgressNotification -> handleProgressNotification(notification.params)
				is LoggingMessageNotification -> handleLoggingMessageNotification(notification.params)
				is PromptListChangedNotification -> handlePromptListChangedNotification()
				is ResourceListChangedNotification -> handleResourceListChangedNotification()
				is ResourceUpdatedNotification -> handleResourceUpdatedNotification(notification.params)
				is RootsListChangedNotification -> handleRootsListChangedNotification()
				is ToolListChangedNotification -> handleToolListChangedNotification()
				else -> logWarning("Received unknown notification: ${notification::class}")
			}
		} catch (e: NotImplementedError) {
			// Subclass didn't implement a particular notification handler
			logWarning("No handler implemented for notification ${notification::class}")
		} catch (e: Throwable) {
			logError("Error handling notification $notification", e)
		}
	}

	// -----------------------------------------------------
	// Incoming Message Handling
	// -----------------------------------------------------

	private suspend fun onMessageLine(line: String) {
		logIncoming(line)

		var id: String? = null
		var method: String? = null
		try {
			val (parsedId, parsedMethod) = extractIdAndMethod(line)
			id = parsedId
			method = parsedMethod

			val message = line.toJsonRpcMessage()
			when (message) {
				is JsonRpcResponse -> handleResponse(message)
				is JsonRpcRequest -> dispatchRequest(message)
				is JsonRpcNotification -> dispatchNotification(message)
				else -> logWarning("Received unknown message type: $line")
			}
		} catch (e: Throwable) {
			logError("Failed to process incoming line: $line", e)
			val (errorCode, errorMsg) = determineErrorResponse(method, e)
			if (id != null) {
				val errorResponse = JsonRpcResponse(
					id = id,
					error = JsonRpcError(code = errorCode, message = errorMsg),
				)
				val serializedError = mcpJson.encodeToString(JsonRpcResponse.serializer(), errorResponse)
				logOutgoing(serializedError)
				transport.writeString(serializedError)
			}
		}
	}

	private fun extractIdAndMethod(line: String): Pair<String?, String?> {
		return try {
			val jsonObj = mcpJson.parseToJsonElement(line).jsonObject
			val idElement = jsonObj["id"]
			val id = if (idElement == null || idElement.toString() == "null") null else idElement.toString().trim('"')
			val method = jsonObj["method"]?.jsonPrimitive?.contentOrNull
			id to method
		} catch (e: Throwable) {
			null to null
		}
	}

	private suspend fun handleResponse(response: JsonRpcResponse) {
		val requestId = response.id
		val deferred = outgoingRequestsMutex.withLock {
			outgoingRequests.remove(requestId)
		}
		if (deferred == null) {
			logWarning("Received response for unknown request ID: $requestId")
		} else {
			deferred.complete(response)
		}
	}

	/**
	 * Dispatches an incoming request by launching a child coroutine for it.
	 * The job is stored in [incomingRequests], so we can cancel it if needed.
	 */
	private suspend fun dispatchRequest(request: JsonRpcRequest) {
		val job = scope.launch {
			val response = handleRequest(request)
			if (this@launch.isActive) {
				val responseLine = mcpJson.encodeToString(JsonRpcResponse.serializer(), response)
				logOutgoing(responseLine)
				transport.writeString(responseLine)
			}
		}

		// Track this incoming request job by ID
		incomingRequestsMutex.withLock {
			incomingRequests[request.id] = job
		}

		// Once completed, remove it from the map
		job.invokeOnCompletion {
			scope.launch {
				incomingRequestsMutex.withLock {
					incomingRequests.remove(request.id)
				}
			}
		}
	}

	private suspend fun dispatchNotification(notification: JsonRpcNotification) {
		try {
			handleNotification(notification)
		} catch (e: Throwable) {
			logError("Error handling notification $notification", e)
		}
	}

	// -----------------------------------------------------
	// Helper & Utility Functions
	// -----------------------------------------------------

	/**
	 * Completes all outgoing requests exceptionally so that waiting callers see [error] as the cause.
	 */
	private suspend fun completeAllOutgoingRequestsExceptionally(error: Throwable) {
		outgoingRequestsMutex.withLock {
			for ((_, deferred) in outgoingRequests) {
				deferred.completeExceptionally(error)
			}
			outgoingRequests.clear()
		}
	}

	private fun JsonRpcRequest.returnErrorResponse(
		message: String,
		code: Int,
	): JsonRpcResponse {
		return JsonRpcResponse(
			id = id,
			error = JsonRpcError(code, message),
		)
	}

	/**
	 * By default, we throw [NotImplementedError] for unimplemented optional parts of the protocol.
	 * Subclasses can override if they want to provide an actual implementation.
	 */
	private fun notImplemented(): Nothing = throw NotImplementedError("Not implemented.")

	// -----------------------------------------------------
	// Logging Utilities
	// -----------------------------------------------------

	// TODO do logging properly

	private suspend fun logIncoming(line: String) = logger?.invoke("INCOMING: $line")

	private suspend fun logOutgoing(line: String) = logger?.invoke("OUTGOING: $line")

	private fun logWarning(message: String) {}

	private fun logError(
		message: String,
		e: Throwable,
	) {
	}
}
