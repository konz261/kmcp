plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
	alias(libs.plugins.ondrsh.kmcp) // Will not use GAV coordinates, will be substituted
}

kotlin {

	iosArm64()
	iosX64()
	iosSimulatorArm64()
	js(IR) { nodejs() }
	jvm()
	linuxX64()
	macosArm64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.ondrsh.jsonschema)
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.kotlinx.coroutines.core)
				api(libs.kotlinx.serialization.core)
				api(libs.kotlinx.serialization.json)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlinx.coroutines.test)
				implementation(kotlin("test"))
			}
		}
	}
}
