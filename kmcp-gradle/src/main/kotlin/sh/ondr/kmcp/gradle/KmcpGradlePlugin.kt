package sh.ondr.kmcp.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class KmcpGradlePlugin : KotlinCompilerPluginSupportPlugin {
	override fun apply(target: Project) {
	}

	override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

	override fun getCompilerPluginId(): String = "${BuildConfig.PLUGIN_GROUP}.compiler"

	override fun getPluginArtifact(): SubpluginArtifact =
		SubpluginArtifact(
			groupId = BuildConfig.PLUGIN_GROUP,
			artifactId = "kmcp-compiler",
			version = BuildConfig.PLUGIN_VERSION,
		)

	override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
		val project = kotlinCompilation.target.project
		return project.provider {
			listOf(SubpluginOption(key = "enabled", value = "true"))
		}
	}
}
