package sh.ondr.kmcp.runtime.resources

import sh.ondr.kmcp.schema.resources.Resource
import sh.ondr.kmcp.schema.resources.ResourceContents
import sh.ondr.kmcp.schema.resources.ResourceTemplate

/**
 * Provides access to a set of resources and (optionally) notifies about changes.
 *
 * Subclasses can override [supportsSubscriptions] to indicate whether they can emit
 * resource change notifications via [onResourceChange] or [onResourcesListChanged].
 */
abstract class ResourceProvider {
	/**
	 * Whether this provider can emit resource change notifications.
	 */
	abstract val supportsSubscriptions: Boolean

	/**
	 * Implementations should call this when a specific resource changes. By default, no-op.
	 */
	protected var onResourceChange: suspend (uri: String) -> Unit = {}

	/**
	 * Implementations should call this when the overall list of resources changes (new ones added, removed, etc.).
	 * By default, no-op.
	 */
	protected var onResourcesListChanged: suspend () -> Unit = {}

	/**
	 * Sets callbacks for resource change notifications. Called by the server/manager.
	 */
	open fun attachCallbacks(
		onResourceChange: suspend (String) -> Unit,
		onResourcesListChanged: suspend () -> Unit,
	) {
		this.onResourceChange = onResourceChange
		this.onResourcesListChanged = onResourcesListChanged
	}

	/**
	 * Returns the list of currently available resources.
	 */
	open suspend fun listResources(): List<Resource> = listOf()

	/**
	 * Fetches the contents of the given resource URI, or `null` if unavailable.
	 */
	abstract suspend fun readResource(uri: String): ResourceContents?

	/**
	 * Returns any resource templates this provider supports, e.g. `file:///{path}`.
	 */
	open suspend fun listResourceTemplates(): List<ResourceTemplate> = listOf()
}
