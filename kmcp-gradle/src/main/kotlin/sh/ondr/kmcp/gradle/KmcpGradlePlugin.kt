package sh.ondr.kmcp.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import sh.ondr.koja.gradle.KojaGradlePlugin
import kotlin.jvm.java

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class KmcpGradlePlugin : KotlinCompilerPluginSupportPlugin {
	override fun apply(target: Project) {
		val kspDependency = target.getKspDependency()
		val runtimeDependency = target.getRuntimeDependency()

		// Immediately fail if Koja is already applied
		if (target.plugins.hasPlugin("sh.ondr.koja")) {
			error(
				"Kolli plugin cannot be used together with the Koja plugin. " +
					"Remove the 'sh.ondr.koja' plugin from your build.",
			)
		}

		// Or fail if Koja is applied later
		target.pluginManager.withPlugin("sh.ondr.koja") {
			error(
				"Kolli plugin cannot be used together with the Koja plugin. " +
					"Remove the 'sh.ondr.koja' plugin from your build.",
			)
		}

		// Apply koja plugin
		val kojaGradlePlugin = KojaGradlePlugin()
		kojaGradlePlugin.apply(target)

		// Specifically add koja compiler plugin to the classpath
		val configuration = target.configurations.getByName("kotlinCompilerPluginClasspath")
		configuration.dependencies.add(
			target.dependencies.create("sh.ondr.koja:koja-compiler:0.2.0"),
		)

		// Apply in any case
		target.pluginManager.apply("com.google.devtools.ksp")

		// Apply to Kotlin Multiplatform projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
			// Add jsonschema and runtime to commonMain
			kotlin.sourceSets.getByName("commonMain").dependencies {
				if (target.name != "kmcp-runtime") {
					implementation(runtimeDependency)
				}
			}

			// Add KSP dependency for all Kotlin targets main compilations
			kotlin.targets.configureEach { kotlinTarget ->
				kotlinTarget.compilations.configureEach { compilation ->
					if (compilation.name == "main" && kotlinTarget.name != "metadata") {
						target.dependencies.add(
							"ksp${kotlinTarget.name.replaceFirstChar { it.uppercase() }}",
							kspDependency,
						)
					}
					if (compilation.name == "test" && kotlinTarget.name != "metadata") {
						target.dependencies.add(
							"ksp${kotlinTarget.name.replaceFirstChar { it.uppercase() }}Test",
							kspDependency,
						)
					}
				}
			}
		}

		// Apply to pure JVM projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
			val kotlinJvm = target.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java)
			// Add jsonschema and runtime to main
			kotlinJvm.sourceSets.getByName("main").dependencies {
				if (target.name != "kmcp-runtime") {
					implementation(runtimeDependency)
				}
			}

			// Add KSP for main
			target.dependencies.add("ksp", kspDependency)
			// Add KSP for test
			target.dependencies.add("kspTest", kspDependency)
		}
	}

	fun Project.getRuntimeDependency() =
		if (isInternalBuild()) {
			project(":kmcp-runtime")
		} else {
			"sh.ondr.kmcp:kmcp-runtime:${PLUGIN_VERSION}"
		}

	fun Project.getKspDependency() =
		if (isInternalBuild()) {
			project(":kmcp-ksp")
		} else {
			"sh.ondr.kmcp:kmcp-ksp:${PLUGIN_VERSION}"
		}

	fun Project.isInternalBuild() = findProperty("sh.ondr.kmcp.internal") == "true"

	override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

	override fun getCompilerPluginId(): String = "sh.ondr.kmcp"

	override fun getPluginArtifact(): SubpluginArtifact =
		SubpluginArtifact(
			groupId = "sh.ondr.kmcp",
			artifactId = "kmcp-compiler",
			version = PLUGIN_VERSION,
		)

	override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
		val project = kotlinCompilation.target.project
		return project.provider {
			listOf(SubpluginOption(key = "enabled", value = "true"))
		}
	}
}
