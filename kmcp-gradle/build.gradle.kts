import com.vanniktech.maven.publish.SonatypeHost

plugins {
	id("java-gradle-plugin")
	alias(libs.plugins.build.config)
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
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

val pluginGroup = rootProject.findProperty("group") as String
val pluginVersion = rootProject.findProperty("version") as String

buildConfig {
	packageName("$pluginGroup.gradle")
	buildConfigField("String", "PLUGIN_GROUP", "\"$pluginGroup\"")
	buildConfigField("String", "PLUGIN_VERSION", "\"$pluginVersion\"")
}

gradlePlugin {
	plugins {
		create("main") {
			id = pluginGroup
			implementationClass = "$pluginGroup.gradle.KmcpGradlePlugin"
			version = pluginVersion
		}
	}
}

mavenPublishing {
	publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
	signAllPublications()

	coordinates(
		groupId = project.group.toString(),
		artifactId = "kmcp-gradle",
		version = project.version.toString(),
	)

	pom {
		name = "KMCP Gradle Plugin"
		description = "KMCP: Kotlin Multiplatform MCP Framework Gradle Plugin"
		inceptionYear = "2024"
		url = "https://github.com/ondrsh/kmcp"
		licenses {
			license {
				name = "Apache License 2.0"
				url = "https://www.apache.org/licenses/LICENSE-2.0"
				distribution = "repo"
			}
		}
		developers {
			developer {
				id = "ondrsh"
				name = "Andreas Toth"
				url = "https://github.com/ondrsh"
			}
		}
		scm {
			url = "https://github.com/ondrsh/kmcp"
			connection = "scm:git:git://github.com/ondrsh/kmcp.git"
			developerConnection = "scm:git:ssh://git@github.com/ondrsh/kmcp.git"
		}
	}
}
