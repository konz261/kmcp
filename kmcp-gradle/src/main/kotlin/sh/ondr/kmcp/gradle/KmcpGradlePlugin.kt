package sh.ondr.kmcp.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import kotlin.jvm.java

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class KmcpGradlePlugin : KotlinCompilerPluginSupportPlugin {
	override fun apply(target: Project) {
		val runtimeDependency = "${BuildConfig.PLUGIN_GROUP}:kmcp-runtime:${BuildConfig.PLUGIN_VERSION}"
		val jsonSchemaDependency = "sh.ondr:kotlin-json-schema:0.1.0"
		val kspDependency = "${BuildConfig.PLUGIN_GROUP}:kmcp-ksp:${BuildConfig.PLUGIN_VERSION}"

		// Apply in any case
		target.pluginManager.apply("com.google.devtools.ksp")

		// Apply to Kotlin Multiplatform projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
			// Add runtime to commonMain
			kotlin.sourceSets.getByName("commonMain").dependencies {
				implementation(runtimeDependency)
				implementation(jsonSchemaDependency)
			}

			// Add KSP dependency for all Kotlin targets
			kotlin.targets.configureEach { kotlinTarget ->
				kotlinTarget.compilations.configureEach { compilation ->
					if (compilation.name == "main" && kotlinTarget.name != "metadata") {
						target.dependencies.add(
							"ksp${kotlinTarget.name.replaceFirstChar { it.uppercase() } }",
							kspDependency,
						)
					}
				}
			}
		}

		// Apply to pure JVM projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
			val kotlinJvm = target.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java)
			// Add runtime
			kotlinJvm.sourceSets.getByName("main").dependencies {
				implementation(runtimeDependency)
				implementation(jsonSchemaDependency)
			}

			// Add KSP dependency for JVM
			target.dependencies.add("ksp", kspDependency)
		}
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
