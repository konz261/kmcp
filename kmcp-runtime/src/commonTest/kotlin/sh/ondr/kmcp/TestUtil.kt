package sh.ondr.kmcp

import kotlin.test.fail

/**
 * A small DSL entry point.
 *
 * Usage:
 * ```
 * val expected = logLines {
 *     clientOutgoing("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
 *     serverIncoming("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
 *     serverOutgoing("""{"jsonrpc":"2.0","id":"2","result":{}}""")
 *     clientIncoming("""{"jsonrpc":"2.0","id":"2","result":{}}""")
 * }
 * assertLinesMatch(expected, log, "ping test")
 * ```
 */
fun logLines(buildBlock: LogAssertionBuilder.() -> Unit): List<String> {
	val builder = LogAssertionBuilder()
	builder.buildBlock()
	return builder.build()
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
			sb.append("Line $i does not match$prefix.\n")
			sb.append("Expected: ${expected[i]}\n")
			sb.append("Actual:   ${actual[i]}\n\n")
			sb.append("Full expected lines:\n")
			expected.forEach { sb.append(" E: $it\n") }
			sb.append("\nFull actual lines:\n")
			actual.forEach { sb.append(" A: $it\n") }

			fail(sb.toString())
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

		fail(sb.toString())
	}

	// If we get here, all lines match exactly.
}

class LogAssertionBuilder {
	private val expectedLines = mutableListOf<String>()

	fun clientOutgoing(json: String) {
		expectedLines += """CLIENT OUTGOING: $json"""
	}

	fun clientIncoming(json: String) {
		expectedLines += """CLIENT INCOMING: $json"""
	}

	fun serverOutgoing(json: String) {
		expectedLines += """SERVER OUTGOING: $json"""
	}

	fun serverIncoming(json: String) {
		expectedLines += """SERVER INCOMING: $json"""
	}

	fun build() = expectedLines.toList()
}
