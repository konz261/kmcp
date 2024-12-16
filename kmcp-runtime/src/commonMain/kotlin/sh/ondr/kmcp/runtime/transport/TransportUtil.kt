package sh.ondr.kmcp.runtime.transport

suspend fun runTransportLoop(
	transport: Transport,
	onMessageLine: suspend (String) -> Unit,
	onError: (suspend (Throwable) -> Unit)? = null,
	onClose: (suspend () -> Unit)? = null,
) {
	try {
		while (true) {
			val line = transport.readString() ?: break
			onMessageLine(line)
		}
		// If we broke out of the loop, it means readString() returned null => closed
		onClose?.invoke()
	} catch (e: Throwable) {
		onError?.invoke(e)
	} finally {
		transport.close()
	}
}
