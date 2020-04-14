
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
//    id("org.jetbrains.kotlin.jvm") version "1.3.40"
    kotlin("jvm") version "1.3.71"
    kotlin("kapt") version "1.3.71"
}

group = "com.warburg"
version ="0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven { setUrl("https://dl.bintray.com/hotkeytlt/maven") }
    maven { url = URI("https://dl.bintray.com/juanchosaravia/autodsl") }
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api("com.github.h0tk3y.betterParse:better-parse-jvm:0.4.0-alpha-3")

    kapt("com.juanchosaravia.autodsl:processor:0.0.9")
    implementation("com.juanchosaravia.autodsl:annotation:0.0.9")

    implementation("me.tomassetti:kllvm:0.1.0")

    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.0")
    testImplementation("io.mockk:mockk:1.9")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType(KotlinJvmCompile::class.java) {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions {
        freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}