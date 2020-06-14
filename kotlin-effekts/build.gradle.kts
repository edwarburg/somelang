
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.3.71"
}

group = "com.warburg"
version ="0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.71")

    val junitVersion = "5.3.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("io.mockk:mockk:1.9")
    testImplementation( "org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.7")
}

tasks.test {
    useJUnitPlatform()
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}