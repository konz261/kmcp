import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
	alias(libs.plugins.dokka)
}

kotlin {

	iosArm64()
	iosX64()
	iosSimulatorArm64()
	js(IR) {
		nodejs()
		binaries.library()
	}
	jvm {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
		}
	}
	linuxX64()
	macosArm64()
	macosX64()
	mingwX64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.kotlinx.coroutines.core)
				implementation(libs.kotlinx.serialization.core)
				implementation(libs.kotlinx.serialization.json)
				implementation(libs.koja.runtime)
			}
		}
	}
}

mavenPublishing {
	configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
}
