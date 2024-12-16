plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
}

dependencies {
	kotlin {
		jvm()

		js(IR) { nodejs() }

		macosArm64()

		listOf(
			iosX64(),
			iosArm64(),
		)

		sourceSets {
			commonMain {
				dependencies {
					implementation("sh.ondr:kotlin-json-schema:0.1.0")
					implementation(libs.coroutines.core)
					api(libs.kotlinx.serialization.core)
					api(libs.kotlinx.serialization.json)
				}
			}
			commonTest {
				dependencies {
					implementation(libs.coroutines.test)
					implementation(kotlin("test"))
				}
			}
		}
	}
}
