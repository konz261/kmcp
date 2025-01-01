@file:OptIn(ExperimentalEncodingApi::class)

package sh.ondr.kmcp.runtime.resources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import sh.ondr.kmcp.schema.resources.Resource
import sh.ondr.kmcp.schema.resources.ResourceContents
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A [ResourceProvider] that exposes a finite, discrete set of files (by relative path)
 * from a given [rootDir]. It does NOT return any resource templates.
 *
 * Example usage:
 *
 * ```
 * val fileSystem: FileSystem = FileSystem.SYSTEM
 * val provider = DiscreteFileProvider(
 *     fileSystem = fileSystem,
 *     rootDir = "/some/local/folder".toPath(),
 *     knownFiles = listOf("notes.txt", "report.pdf")
 * )
 * ```
 *
 * Once created, [listResources] will show the above files, each accessible via a `file://`
 * URI like `file://notes.txt`.
 *
 * If you need to add or remove files at runtime, call [addFile] or [removeFile].
 */
class DiscreteFileProvider(
	private val fileSystem: FileSystem,
	private val rootDir: Path,
	knownFiles: List<String> = emptyList(),
) : ResourceProvider() {
	override val supportsSubscriptions: Boolean = true

	private val files = knownFiles.toMutableList()

	/**
	 * Lists the currently known, discrete resources. Each resource is associated with a
	 * `file://relativePath` URI.
	 */
	override suspend fun listResources(): List<Resource> {
		return files.map { relativePath ->
			val fullPath = rootDir.resolve(relativePath)
			val name = fullPath.name
			Resource(
				uri = "file://$relativePath",
				name = name,
				description = "File at $relativePath",
				mimeType = guessMimeType(name),
			)
		}
	}

	/**
	 * Reads file contents if [uri] starts with `file://` and matches one of the known files.
	 * If the file doesn't exist or is a directory, returns null.
	 */
	override suspend fun readResource(uri: String): ResourceContents? =
		withContext(Dispatchers.Default) {
			// Expecting uri like "file://someRelativePath"
			if (!uri.startsWith("file://")) return@withContext null
			val relativePath = uri.removePrefix("file://")

			// If it's not in our known files list, then we treat it as "not found".
			if (!files.contains(relativePath)) return@withContext null

			val fullPath = rootDir.resolve(relativePath)
			if (!fileSystem.exists(fullPath)) return@withContext null
			if (fileSystem.metadata(fullPath).isDirectory) return@withContext null

			// Read file
			val source = fileSystem.source(fullPath)
			val data = source.buffer().use { it.readByteArray() }

			// For simplicity, guess text if it’s "text/" and otherwise return a blob.
			val mimeType = guessMimeType(relativePath)
			if (mimeType.startsWith("text")) {
				val text = runCatching { data.decodeToString() }.getOrNull() ?: return@withContext null
				ResourceContents.Text(
					uri = uri,
					mimeType = mimeType,
					text = text,
				)
			} else {
				ResourceContents.Blob(
					uri = uri,
					mimeType = mimeType,
					blob = Base64.encode(data),
				)
			}
		}

	/**
	 * Dynamically adds a file to this provider's known files list.
	 * If it wasn't in the list before, triggers onResourcesListChanged() so that clients
	 * can be notified of an updated resource listing (if subscriptions are in place).
	 */
	suspend fun addFile(relativePath: String) {
		if (!files.contains(relativePath)) {
			files += relativePath
			onResourcesListChanged()
		}
	}

	/**
	 * Removes a file from the known list, triggering onResourcesListChanged() if removed.
	 */
	suspend fun removeFile(relativePath: String) {
		if (files.remove(relativePath)) {
			onResourcesListChanged()
		}
	}

	/**
	 * Notifies that a file has been updated. If a client is subscribed to changes for
	 * `file://relativePath`, this triggers a `notifications/resources/updated`.
	 */
	suspend fun notifyResourceUpdated(relativePath: String) {
		if (files.contains(relativePath)) {
			onResourceChange("file://$relativePath")
		}
	}

	// Everything is "text/plain" for now
	// TODO implement proper mime type detection
	private fun guessMimeType(relativePath: String): String {
		return "text/plain"
	}
}
