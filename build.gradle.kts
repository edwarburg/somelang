
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.3.71"
}

group = "com.warburg"
version ="0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven { setUrl("https://dl.bintray.com/hotkeytlt/maven") }
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api("com.github.h0tk3y.betterParse:better-parse-jvm:0.4.0-alpha-3")

    val asmVersion = "8.0.1"
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("net.bytebuddy:byte-buddy:1.10.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.71")

    implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.6")

    val junitVersion = "5.3.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("io.mockk:mockk:1.9")
    testImplementation( "org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.7")
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