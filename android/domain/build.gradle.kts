// Pure-Kotlin domain module: models (shared JSON contracts) and business logic with no Android deps.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Unit tests read the real shared contracts so both platforms are tested against the same data.
tasks.withType<Test> { systemProperty("autoverse.repoRoot", rootProject.projectDir.parentFile.absolutePath) }
