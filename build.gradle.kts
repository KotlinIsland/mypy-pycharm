/*
 * Copyright 2018 Roberto Leinardi.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    java
    // Kotlin support
    kotlin("jvm") version "1.6.10"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.3.1"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "1.3.1"
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "0.1.13"
}

group = properties("pluginGroup")
version = properties("version")

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
    downloadSources.set(properties("platformDownloadSources").toBoolean())
    updateSinceUntilBuild.set(true)

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    showReport.set(System.getenv("QODANA_SHOW_REPORT").toBoolean())
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
            options.isDeprecation = true
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }


    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
                projectDir.resolve("README.md").readText().lines().run {
                    val start = "<!-- Plugin description -->"
                    val end = "<!-- Plugin description end -->"

                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end))
                }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.run {
                getOrNull(properties("pluginVersion")) ?: getLatest()
            }.toHTML()
        })
    }

    runPluginVerifier {
        ideVersions.set(properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty))
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }
}

//checkstyle {
//    ignoreFailures = false // Whether this task will ignore failures and continue running the build.
//    configFile rootProject.file('config/checkstyle/checkstyle.xml')
//     The Checkstyle configuration file to use.
//    toolVersion = '8.29' // The version of Checkstyle you want to be used
//}

//def hasPyCharm = project.hasProperty('pycharmPath')
//def hasPythonPlugin = project.hasProperty('pythonPlugin')
//def props = new Properties()
//rootProject.file('src/main/resources/com/leinardi/pycharm/mypy/MypyBundle.properties')
//        .withInputStream {
//            props.load(it)
//        }

//gradle.projectsEvaluated {
//    tasks.withType(JavaCompile) {
//        options.compilerArgs << '-Xlint:unchecked' << '-Xlint:deprecation'
//    }
//}


//    pluginName props.getProperty('plugin.name').toLowerCase().replace(' ', '-')
//    downloadSources Boolean.valueOf(downloadIdeaSources)
//    updateSinceUntilBuild = true
//    if (hasPyCharm) {
//        alternativeIdePath pycharmPath
//    }
//    if (hasPythonPlugin) {
//        plugins += [pythonPlugin]
//    }

//patchPluginXml {
//    version project.property('version')
//    sinceBuild project.property('sinceBuild')
//    untilBuild project.property('untilBuild')
//    pluginDescription props.getProperty('plugin.Mypy-PyCharm.description')
//    changeNotes getChangelogHtml()
//}

//publishPlugin {
//    def publishToken = project.hasProperty('jetbrainsPublishToken') ? jetbrainsPublishToken : ""
//    token publishToken
//    channels publishChannels
//}

//repositories {
//    maven { url "https://plugins.gradle.org/m2/" }
//    maven { url 'https://dl.bintray.com/jetbrains/intellij-plugin-service' }
//    if (hasPyCharm) {
//        flatDir {
//            dirs "$pycharmPath/lib"
//        }
//    }
//}

//dependencies {
//    if (hasPyCharm) {
//        compileOnly name: 'pycharm'
//    }
//    errorprone 'com.google.errorprone:error_prone_core:2.3.1'
//}

//def getChangelogHtml() {
//    Parser parser = Parser.builder().build()
//    Node document = parser.parseReader(rootProject.file('CHANGELOG.md').newReader())
//    HtmlRenderer renderer = HtmlRenderer.builder().build()
//    renderer.render(document.firstChild.next)
//}
//
//check.dependsOn(verifyPlugin)
//
//task violationCommentsToGitHub(type: se.bjurr.violations.comments.github.plugin.gradle.ViolationCommentsToGitHubTask) {
//    repositoryOwner = "leinardi"
//    repositoryName = "mypy-pycharm"
//    pullRequestId = System.properties['GITHUB_PULLREQUESTID']
//    oAuth2Token = System.properties['GITHUB_OAUTH2TOKEN']
//    gitHubUrl = "https://api.github.com/"
//    createCommentWithAllSingleFileComments = false
//    createSingleFileComments = true
//    commentOnlyChangedContent = true
//    violations = [
//            ["FINDBUGS", ".", ".*/reports/findbugs/.*\\.xml\$", "Findbugs"],
//            ["CHECKSTYLE", ".", ".*/reports/checkstyle/.*debug\\.xml\$", "Checkstyle"],
//            ["ANDROIDLINT", ".", ".*/reports/lint-results.*\\.xml\$", "Android Lint"],
//            ["GOOGLEERRORPRONE", ".", ".*/build.log\$", "Error Prone"]
//    ]
//}
