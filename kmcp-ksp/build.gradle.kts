plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.ksp)
	alias(libs.plugins.maven.publish)
}

dependencies {
	implementation(libs.auto.service.annotations)
	implementation(libs.ksp.api)
}
