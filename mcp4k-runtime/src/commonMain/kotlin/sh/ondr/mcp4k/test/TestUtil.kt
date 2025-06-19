package sh.ondr.mcp4k.test

/**
 * A small DSL entry point for building expected log sequences in tests.
 *
 * Usage:
 * ```
 * val expected = buildLog {
 *     addClientOutgoing("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
 *     addServerIncoming("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
 *     addServerOutgoing("""{"jsonrpc":"2.0","id":"2","result":{}}""")
 *     addClientIncoming("""{"jsonrpc":"2.0","id":"2","result":{}}""")
 * }
 * assertLinesMatch(expected, log, "ping test")
 * ```
 */
fun buildLog(buildBlock: LogAssertionBuilder.() -> Unit): List<String> {
	val builder = LogAssertionBuilder()
	builder.buildBlock()
	return builder.build()
}

fun clientIncoming(msg: String) = "CLIENT INCOMING: $msg"

fun clientOutgoing(msg: String) = "CLIENT OUTGOING: $msg"

fun serverIncoming(msg: String) = "SERVER INCOMING: $msg"

fun serverOutgoing(msg: String) = "SERVER OUTGOING: $msg"

class LogAssertionBuilder {
	private val expectedLines = mutableListOf<String>()

	fun addClientOutgoing(msg: String) {
		expectedLines += clientOutgoing(msg)
	}

	fun addClientIncoming(msg: String) {
		expectedLines += clientIncoming(msg)
	}

	fun addServerOutgoing(msg: String) {
		expectedLines += serverOutgoing(msg)
	}

	fun addServerIncoming(msg: String) {
		expectedLines += serverIncoming(msg)
	}

	fun build() = expectedLines.toList()
}

fun assertLinesMatch(
	expected: List<String>,
	actual: List<String>,
	context: String = "",
) {
	val prefix = if (context.isNotEmpty()) " for $context" else ""

	// Compare line by line up to the smaller size
	val minSize = minOf(expected.size, actual.size)
	for (i in 0 until minSize) {
		if (expected[i] != actual[i]) {
			// Found mismatch
			val sb = StringBuilder()
			sb.append("line[$i] does not match$prefix.\n")
			sb.append("Expected: ${expected[i]}\n")
			sb.append("Actual:   ${actual[i]}\n\n")
			sb.append("Full expected lines:\n")
			expected.forEach { sb.append(" E: $it\n") }
			sb.append("\nFull actual lines:\n")
			actual.forEach { sb.append(" A: $it\n") }

			throw AssertionError(sb.toString())
		}
	}

	// If we reach here, lines in the overlapping portion all match.
	// Check if sizes differ
	if (expected.size != actual.size) {
		val sb = StringBuilder()
		sb.append("Number of log lines does not match$prefix\n")
		sb.append("Expected count: ${expected.size}\n")
		sb.append("Actual count: ${actual.size}\n\n")
		sb.append("Full expected lines:\n")
		expected.forEach { sb.append(" E: $it\n") }
		sb.append("\nFull actual lines:\n")
		actual.forEach { sb.append(" A: $it\n") }

		throw AssertionError(sb.toString())
	}

	// If we get here, all lines match exactly.
}
