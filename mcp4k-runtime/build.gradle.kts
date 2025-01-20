plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
	alias(libs.plugins.ondrsh.mcp4k) // Will not use GAV coordinates, will be substituted
}

kotlin {

	iosArm64()
	iosX64()
	iosSimulatorArm64()
	js(IR) {
		nodejs()
		binaries.library()
	}
	jvm()
	linuxX64()
	macosArm64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.kotlinx.coroutines.core)
				implementation(libs.square.okio)
				implementation(libs.square.okio.fakefilesystem)
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
