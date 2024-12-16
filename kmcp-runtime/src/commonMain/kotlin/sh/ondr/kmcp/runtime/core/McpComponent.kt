package sh.ondr.kmcp.runtime.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.ondr.kmcp.runtime.serialization.serializeToString
import sh.ondr.kmcp.runtime.serialization.toJsonRpcMessage
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.runtime.transport.runTransportLoop
import sh.ondr.kmcp.schema.core.JsonRpcError
import sh.ondr.kmcp.schema.core.JsonRpcErrorCodes
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.JsonRpcResponse
import kotlin.coroutines.CoroutineContext

abstract class McpComponent(
	private val transport: Transport,
	private val basicRawLogger: ((String) -> Unit)? = null,
	private val coroutineContext: CoroutineContext = Dispatchers.Default,
) {
	private val scope = CoroutineScope(coroutineContext + SupervisorJob())

	// Maps request IDs to CompletableDeferred awaiting a response.
	private val pendingRequests = mutableMapOf<Long, CompletableDeferred<JsonRpcResponse>>()
	private val pendingRequestsMutex = Mutex()

	// HANDLE THIS
	private var nextRequestId = 1L
	private val requestIdMutex = Mutex()

	/**
	 * Abstract methods that clients and servers must implement:
	 * 1) handleRequest - Given a JsonRpcRequest, return a JsonRpcResponse or null
	 *    * Return null to indicate "Method not found"
	 *    * Throw exceptions for internal errors
	 *    * Or return an error response directly for known error conditions (like invalid params)
	 * 2) handleNotification - Given a JsonRpcNotification, handle it (no return)
	 *    * If errors occur, just log them here. Notifications expect no response.
	 * 3) createErrorResponse - Given an id and message, create a JSON-RPC error response.
	 *    * Add parameters if you need to specify error codes or extra data.
	 */
	protected abstract suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse?

	protected abstract suspend fun handleNotification(notification: JsonRpcNotification)

	protected fun createErrorResponse(
		id: String,
		message: String,
		code: Int,
	): JsonRpcResponse {
		return JsonRpcResponse(
			id = id,
			error =
				JsonRpcError(
					code = code,
					message = message,
				),
		)
	}

	suspend fun start() {
		transport.connect()
		// Launch the reading loop in a coroutine
		scope.launch {
			runTransportLoop(
				transport = transport,
				onMessageLine = { line -> onMessageLine(line) },
				onError = { error -> onTransportError(error) },
				onClose = { onTransportClose() },
			)
		}
	}

	/**
	 * Sends a request and awaits a response asynchronously.
	 */
	suspend fun sendRequest(builder: (id: String) -> JsonRpcRequest): JsonRpcResponse {
		val requestIdLong = requestIdMutex.withLock { nextRequestId++ }
		val requestIdString = requestIdLong.toString()

		val deferred = CompletableDeferred<JsonRpcResponse>()
		pendingRequestsMutex.withLock {
			pendingRequests[requestIdLong] = deferred
		}

		val request: JsonRpcRequest = builder(requestIdString)
		val requestLine = request.serializeToString()
		basicRawLogger?.invoke("OUTGOING: $requestLine")
		transport.writeString(requestLine)

		return deferred.await()
	}

	/**
	 * Sends a notification (no response expected).
	 */
	suspend fun sendNotification(notification: JsonRpcNotification) {
		val notificationLine = notification.serializeToString()
		basicRawLogger?.invoke("OUTGOING: $notificationLine")
		transport.writeString(notificationLine)
	}

	private suspend fun onMessageLine(line: String) {
		try {
			basicRawLogger?.invoke("INCOMING: $line")
			val message = line.toJsonRpcMessage()

			when (message) {
				is JsonRpcResponse -> handleResponse(message)
				is JsonRpcRequest -> dispatchRequest(message)
				is JsonRpcNotification -> dispatchNotification(message)
				else -> {
					// Unknown message type, log or ignore
					logWarning("Received unknown message type: $line")
				}
			}
		} catch (e: Throwable) {
			// Possibly a deserialization error or unexpected issue.
			// We cannot respond because we don't have a valid id if parsing failed.
			// Just log and ignore.
			logError("Failed to process incoming line: $line", e)
		}
	}

	private suspend fun handleResponse(response: JsonRpcResponse) {
		val requestId = response.id.toLongOrNull()
		if (requestId == null) {
			// Invalid response ID, cannot correlate. Log and ignore.
			logWarning("Received response with invalid ID: ${response.id}")
			return
		}

		val deferred =
			pendingRequestsMutex.withLock {
				pendingRequests.remove(requestId)
			}
		if (deferred == null) {
			// No pending request for this ID. Possibly a late or spurious response.
			logWarning("Received response for unknown request ID: ${response.id}")
		} else {
			deferred.complete(response)
		}
	}

	private suspend fun dispatchRequest(request: JsonRpcRequest) {
		val response: JsonRpcResponse =
			try {
				handleRequest(request) ?: createErrorResponse(request.id, "Method not found", code = JsonRpcErrorCodes.METHOD_NOT_FOUND)
			} catch (e: IllegalArgumentException) {
				// Treat IllegalArgumentException as invalid params error
				createErrorResponse(request.id, "Invalid params: ${e.message}", code = JsonRpcErrorCodes.INVALID_PARAMS)
			} catch (e: Throwable) {
				// Any other unexpected error is internal error
				logError("Internal error while handling request $request", e)
				createErrorResponse(request.id, "Internal error: ${e.message ?: "unknown"}", code = JsonRpcErrorCodes.INTERNAL_ERROR)
			}

		val responseLine = response.serializeToString()
		basicRawLogger?.invoke("OUTGOING: $responseLine")
		transport.writeString(responseLine)
	}

	private suspend fun dispatchNotification(notification: JsonRpcNotification) {
		try {
			handleNotification(notification)
		} catch (e: Throwable) {
			// Notifications have no response channel. Just log the error.
			logError("Error handling notification $notification", e)
		}
	}

	protected open suspend fun onTransportClose() {
		// handle close scenario if needed
	}

	protected open suspend fun onTransportError(error: Throwable) {
		// handle error scenario if needed
		// Optionally complete all pending requests with an error.
		completeAllPendingRequestsExceptionally(error)
	}

	private suspend fun completeAllPendingRequestsExceptionally(error: Throwable) {
		pendingRequestsMutex.withLock {
			for ((_, deferred) in pendingRequests) {
				deferred.completeExceptionally(error)
			}
			pendingRequests.clear()
		}
	}

	/**
	 * Log a warning message. Actual logging implementation depends on your platform.
	 */
	private fun logWarning(message: String) {
		println("WARNING: $message")
	}

	/**
	 * Log an error message with an exception stacktrace.
	 */
	private fun logError(
		message: String,
		e: Throwable,
	) {
		println("ERROR: $message\n$e")
	}
}
