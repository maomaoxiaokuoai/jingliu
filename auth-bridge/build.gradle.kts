import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("application")
}

// Compile with the JDK running Gradle (normally Android Studio JBR 17 or 21).
// JVM_17/release 17 is an OUTPUT compatibility target, not a demand to locate another JDK 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
application {
    mainClass.set("com.luma.bridge.MainKt")
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.httpserver")
}
tasks.test { useJUnit(); jvmArgs("--add-modules=jdk.httpserver") }
