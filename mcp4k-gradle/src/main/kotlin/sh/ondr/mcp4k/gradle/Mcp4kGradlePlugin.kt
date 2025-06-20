package sh.ondr.mcp4k.gradle

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
class Mcp4kGradlePlugin : KotlinCompilerPluginSupportPlugin {
	override fun apply(target: Project) {
		val kspDependency = target.getKspDependency()
		val runtimeDependency = target.getRuntimeDependency()

		// Immediately fail if Koja is already applied
		if (target.plugins.hasPlugin("sh.ondr.koja")) {
			error(
				"The Koja plugin should not be applied separately when using mcp4k. " +
					"Remove the 'sh.ondr.koja' plugin from your build - mcp4k will handle it automatically.",
			)
		}

		// Or fail if Koja is applied later
		target.pluginManager.withPlugin("sh.ondr.koja") {
			error(
				"The Koja plugin should not be applied separately when using mcp4k. " +
					"Remove the 'sh.ondr.koja' plugin from your build - mcp4k will handle it automatically.",
			)
		}

		// Apply koja plugin
		val kojaGradlePlugin = KojaGradlePlugin()
		kojaGradlePlugin.apply(target)

		// Apply in any case
		target.pluginManager.apply("com.google.devtools.ksp")

		// Apply to Kotlin Multiplatform projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
			// Add runtime to commonMain
			kotlin.sourceSets.getByName("commonMain").dependencies {
				implementation(runtimeDependency)
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

			kotlin.targets.configureEach { kotlinTarget ->
				kotlinTarget.compilations.configureEach { compilation ->
					val pluginConfigName = "kotlinCompilerPluginClasspath" +
						kotlinTarget.name.replaceFirstChar { it.uppercaseChar() } +
						compilation.name.replaceFirstChar { it.uppercaseChar() }

					val pluginConfig = target.configurations.findByName(pluginConfigName) ?: return@configureEach

					pluginConfig.dependencies.add(
						target.dependencies.create("sh.ondr.koja:koja-compiler:0.4.0"),
					)
				}
			}
		}

		// Apply to pure JVM projects
		target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
			val kotlinJvm = target.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java)
			// Add runtime to main
			kotlinJvm.sourceSets.getByName("main").dependencies {
				implementation(runtimeDependency)
			}

			// Add KSP for main
			target.dependencies.add("ksp", kspDependency)
			// Add KSP for test
			target.dependencies.add("kspTest", kspDependency)
		}
	}

	fun Project.getRuntimeDependency() =
		if (isInternalBuild()) {
			project(":mcp4k-runtime")
		} else {
			"sh.ondr.mcp4k:mcp4k-runtime:${PLUGIN_VERSION}"
		}

	fun Project.getKspDependency() =
		if (isInternalBuild()) {
			project(":mcp4k-ksp")
		} else {
			"sh.ondr.mcp4k:mcp4k-ksp:${PLUGIN_VERSION}"
		}

	fun Project.isInternalBuild() = findProperty("sh.ondr.mcp4k.internal") == "true"

	override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

	override fun getCompilerPluginId(): String = "sh.ondr.mcp4k"

	override fun getPluginArtifact(): SubpluginArtifact =
		SubpluginArtifact(
			groupId = "sh.ondr.mcp4k",
			artifactId = "mcp4k-compiler",
			version = PLUGIN_VERSION,
		)

	override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
		val project = kotlinCompilation.target.project
		val isTestCompilation = (kotlinCompilation.name == "test")

		return project.provider {
			buildList {
				SubpluginOption("enabled", "true")
				SubpluginOption("isTestSet", isTestCompilation.toString())
			}
		}
	}
}
