package sh.ondr.mcp4k.runtime.resources

import sh.ondr.mcp4k.schema.resources.Resource
import sh.ondr.mcp4k.schema.resources.ResourceContents
import sh.ondr.mcp4k.schema.resources.ResourceTemplate

class ResourceProviderManager(
	private val notifyResourceChanged: suspend (String) -> Unit,
	private val notifyResourcesListChanged: suspend () -> Unit,
	builderResourceProviders: List<ResourceProvider>,
) {
	val providers = mutableListOf<ResourceProvider>()

	init {
		builderResourceProviders.forEach { provider ->
			addProvider(provider)
		}
	}

	val supportsSubscriptions: Boolean
		get() = providers.any { it.supportsSubscriptions }

	val subscriptions: MutableSet<String> = mutableSetOf()

	suspend fun onResourceChange(uri: String) {
		if (subscriptions.contains(uri)) {
			notifyResourceChanged(uri)
		}
	}

	suspend fun onResourcesListChanged() {
		notifyResourcesListChanged()
	}

	fun subscribe(uri: String) {
		subscriptions.add(uri)
	}

	fun removeSubscription(uri: String) {
		subscriptions.remove(uri)
	}

	fun addProvider(provider: ResourceProvider) {
		providers += provider
		provider.attachCallbacks(
			onResourceChange = { uri ->
				onResourceChange(uri)
			},
			onResourcesListChanged = {
				onResourcesListChanged()
			},
		)
	}

	suspend fun listResources(): List<Resource> =
		providers
			.flatMap { it.listResources() }
			.distinctBy { it.uri }

	suspend fun readResource(uri: String): ResourceContents? =
		providers.firstNotNullOfOrNull { provider ->
			provider.readResource(uri)
		}

	suspend fun listResourceTemplates(): List<ResourceTemplate> {
		return providers.flatMap { it.listResourceTemplates() }
	}
}
