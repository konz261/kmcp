import com.vanniktech.maven.publish.SonatypeHost

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
	kotlin("kapt")
}

dependencies {
	compileOnly(libs.auto.service.annotations)
	compileOnly(libs.kotlin.compiler.embeddable)
	kapt(libs.auto.service)
	testImplementation(libs.kotlin.compiler.embeddable)
}

mavenPublishing {
	publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
	signAllPublications()

	coordinates(
		groupId = project.group.toString(),
		artifactId = "kmcp-compiler",
		version = project.version.toString(),
	)

	pom {
		name = "KMCP Compiler Plugin"
		description = "KMCP: Kotlin Multiplatform MCP Framework IR Compiler Plugin"
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
