plugins {
	id("java-gradle-plugin")
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
}

dependencies {
	implementation(libs.ksp.api)
	testImplementation(gradleTestKit())
	testImplementation(kotlin("test"))
}
