plugins {
	id("java-gradle-plugin")
	alias(libs.plugins.build.config)
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
}

val pluginGroup = rootProject.findProperty("group") as String
val pluginVersion = rootProject.findProperty("version") as String

buildConfig {
	packageName("$pluginGroup.gradle")
	buildConfigField("String", "PLUGIN_GROUP", "\"$pluginGroup\"")
	buildConfigField("String", "PLUGIN_VERSION", "\"$pluginVersion\"")
}

dependencies {
	compileOnly(libs.auto.service)
	compileOnly(libs.auto.service.annotations)
	compileOnly(libs.kotlin.compiler.embeddable)
	implementation(libs.kotlin.stdlib)
	implementation(libs.kotlin.gradle.api)
	implementation(libs.kotlin.gradle.plugin)
	implementation(libs.ksp.gradle.plugin)
}
