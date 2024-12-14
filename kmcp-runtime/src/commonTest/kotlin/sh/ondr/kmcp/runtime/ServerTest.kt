package sh.ondr.kmcp.runtime

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import sh.ondr.kmcp.runtime.annotation.Tool
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.content.ToolContent
import kotlin.test.Test

class ServerTest {
	@OptIn(InternalSerializationApi::class)
	@Test
	fun editFiles() {
		/**
		 * Represents a single edit operation to perform on a file.
		 *
		 * @param oldText Text to search for - must match exactly
		 * @param newText Text to replace with
		 */
		@Serializable
		data class EditOperation(
			val oldText: String,
			val newText: String,
		)

		fun String.toContent() = TextContent(text = this)

		/**
		 * @param path Path to the file to edit
		 * @param edits List of edit operations to perform
		 * @param dryRun If true, only preview the changes without actually editing the file
		 */
		@Serializable
		data class EditFileParameters(
			val path: String,
			val edits: List<EditOperation>,
			val dryRun: Boolean = false,
		) {
			@Tool("Edits file")
			fun editFile(): ToolContent {
				if (dryRun) {
					println("Previewing changes in $path (dry run):")
				} else {
					println("Editing file $path:")
				}
				for (edit in edits) {
					println(" - Replacing '${edit.oldText}' with '${edit.newText}'")
				}
				return "Success".toContent()
			}
		}

		println("RUNTIME")

		val server =
			Server.Builder()
				.withTool(EditFileParameters::editFile)
				.build()

		server.tools.values.map {
			// for debugging
			println(KMCP.json.encodeToJsonElement(it))
		}

		val editFileArgs =
			EditFileParameters(
				path = "example.txt",
				edits =
					listOf(
						EditOperation("foo", "bar"),
						EditOperation("baz", "qux"),
					),
				dryRun = true,
			)
		val requestParams = KMCP.json.encodeToJsonElement(editFileArgs)
		val callToolResult = server.callTool("editFile", requestParams)
		println(KMCP.json.encodeToJsonElement(callToolResult))
	}
}
