package sh.ondr.kmcp.ksp

data class KDocDescription(
	val description: String?,
	val parameterDescriptions: Map<String, String>,
)

private val allowedTags =
	setOf(
		"@param",
	)

/**
 * Parses KDoc into a main description and parameter descriptions for `@McpTool` annotated functions.
 *
 * Restrictions:
 * - Whitespaces and tab characters are normalized to single spaces. Newlines are removed.
 * - Only `@param` tags are allowed. Any other `@` usage (e.g. `@return`) causes an error.
 * - `@param` must be followed by a known parameter name and a description.
 * - The main description ends when we encounter an `@param` tag.
 * - Having two `@param` tags with the same parameter name is an error.
 *
 * Steps:
 * 1. Normalize whitespace and tokenize the entire docstring into space-separated tokens.
 * 2. Verify that any token containing a `@` is exactly `@param`, otherwise throw an error.
 * 3. The main description is all tokens before the first `@param`.
 * 4. For each `@param`, the next token is the parameter name (must be in [parameters]).
 * 5. Subsequent tokens until next `@param` or end form that parameter's description. If no description, error.
 * 6. If a parameter is mentioned in @param but not known, error.
 *
 * If any rule is violated, we throw, explaining the issue.
 */
fun String.parseDescription(parameters: List<String>): KDocDescription {
	// Step 1: Normalize whitespace
	val normalized =
		this
			.replace('\n', ' ')
			.replace('\r', ' ')
			.replace('\t', ' ')
			.replace(Regex("\\s+"), " ")
			.trim()

	if (normalized.isEmpty()) {
		return KDocDescription(null, emptyMap())
	}

	val tokens = normalized.split(' ')

	// Step 2: Verify allowed tags
	tokens.forEachIndexed { i, t ->
		if (t.contains("@") && t !in allowedTags) {
			// Found a tag or @-starting token that's not allowed.
			throw IllegalArgumentException(
				"Unsupported tag '$t' found in KDoc. " +
					"For @McpTool functions, only '@param' is allowed.",
			)
		}
	}

	var index = 0

	// Extract main description: until first @param or end of tokens
	val mainDescTokens = mutableListOf<String>()
	while (index < tokens.size && tokens[index] != "@param") {
		mainDescTokens.add(tokens[index])
		index++
	}

	val mainDescription = mainDescTokens.joinToString(" ").ifBlank { null }
	val paramDescriptions = mutableMapOf<String, String>()

	// Process @param tags, if any
	while (index < tokens.size) {
		val marker = tokens[index]

		if (marker != "@param") {
			// Any unexpected tag here is not allowed, but we already checked tags above.
			// So if we get here, it's just no more @param tags -> break
			break
		}

		index++
		if (index >= tokens.size) {
			throw IllegalArgumentException(
				"'@param' at the end with no parameter name.",
			)
		}
		val paramName = tokens[index]
		if (paramName.startsWith("@")) {
			throw IllegalArgumentException(
				"'@param' not followed by a parameter name.",
			)
		}
		if (paramName !in parameters) {
			throw IllegalArgumentException(
				"'@param $paramName' references unknown parameter.",
			)
		}

		index++
		// Collect param description until next @param or end
		val paramDescTokens = mutableListOf<String>()
		while (index < tokens.size && tokens[index] != "@param") {
			paramDescTokens.add(tokens[index])
			index++
		}

		val paramDesc =
			paramDescTokens.joinToString(" ").ifBlank {
				throw IllegalArgumentException(
					"'@param $paramName' has no description.",
				)
			}

		if (paramName in paramDescriptions) {
			throw IllegalArgumentException(
				"'@param $paramName' is duplicated.",
			)
		}
		paramDescriptions[paramName] = paramDesc
	}

	return KDocDescription(mainDescription, paramDescriptions)
}
