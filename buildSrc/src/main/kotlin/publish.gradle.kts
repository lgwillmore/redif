package buildsrc.convention

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

// Publishes a library module to Maven Central: jar, sources, a javadoc jar holding its Dokka API reference and a POM,
// all signed. `publishAndReleaseToMavenCentral` uploads every module as one deployment; the Release workflow runs it.
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

group = rootProject.group
version = rootProject.version

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = SourcesJar.Sources()))
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = project.name
        // Read late: a module sets its description after applying this convention.
        description = provider { project.description }
        url = "https://github.com/lgwillmore/redif"
        inceptionYear = "2021"

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/mit-license"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "lgwillmore"
                name = "Laurence Willmore"
                url = "https://github.com/lgwillmore"
            }
        }

        scm {
            connection = "scm:git:git://github.com/lgwillmore/redif.git"
            developerConnection = "scm:git:ssh://git@github.com/lgwillmore/redif.git"
            url = "https://github.com/lgwillmore/redif"
        }

        issueManagement {
            system = "GitHub"
            url = "https://github.com/lgwillmore/redif/issues"
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/lgwillmore/redif/tree/main")
            remoteLineSuffix = "#L"
        }
    }
}
