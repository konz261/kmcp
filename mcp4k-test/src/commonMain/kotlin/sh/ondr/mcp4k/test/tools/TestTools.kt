package sh.ondr.mcp4k.test.tools

import kotlinx.serialization.Serializable
import sh.ondr.koja.JsonSchema
import sh.ondr.mcp4k.runtime.annotation.McpTool
import sh.ondr.mcp4k.runtime.core.toTextContent
import sh.ondr.mcp4k.schema.content.TextContent

/**
 * Email data class for testing complex parameters
 * @property title The title of the email
 * @property body The body of the email
 */
@Serializable
@JsonSchema
data class Email(
	val title: String,
	val body: String?,
)

/**
 * Sends an email to multiple recipients
 * @param recipients The list of recipients
 */
@McpTool
fun sendEmail(
	recipients: List<String>,
	email: Email,
): TextContent = "Email '${email.title}' sent to ${recipients.joinToString(", ")}".toTextContent()

/**
 * Reverses a string
 * @param s The string to reverse
 */
@McpTool
fun reverseString(s: String): TextContent = s.reversed().toTextContent()

/**
 * Tool that was never registered - for testing error handling
 * @param value Some value
 */
@McpTool
fun neverRegisteredTool(value: String): TextContent = "This should never be called: $value".toTextContent()
