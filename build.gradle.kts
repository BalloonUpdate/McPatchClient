import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun getVersionName(tagName: String) = if(tagName.startsWith("v")) tagName.substring(1) else tagName
val gitTagName: String? get() = Regex("(?<=refs/tags/).*").find(System.getenv("GITHUB_REF") ?: "")?.value
val gitCommitSha: String? get() = System.getenv("GITHUB_SHA") ?: null
val debugVersion: String get() = System.getenv("DBG_VERSION") ?: "0.0.0"

group = "com.github.balloonupdate"
version = gitTagName?.run { getVersionName(this) } ?: debugVersion

plugins {
    kotlin("jvm") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/apache-ant-1.10.12.jar"))
    implementation("com.github.lookfirst:sardine:5.10")
    implementation("com.hierynomus:sshj:0.34.0")
    implementation("org.json:json:20220924")
    implementation("org.yaml:snakeyaml:1.33")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.formdev:flatlaf:2.6")
    implementation("com.formdev:flatlaf-intellij-themes:2.6")
    implementation("org.apache.commons:commons-compress:1.23.0")
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    manifest {
        attributes("Version" to archiveVersion.get())
        attributes("Git-Commit" to (gitCommitSha ?: ""))
        attributes("Main-Class" to "mcpatch.McPatchClient")
        attributes("Premain-Class" to "mcpatch.McPatchClient")
    }

    archiveClassifier.set("")
}