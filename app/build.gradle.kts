import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.memorysteps"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.example.memorysteps"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    androidResources { noCompress += "gguf" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val verifyQuestionModel by tasks.registering {
    val model = file("src/main/assets/models/question-author-v2-q8.gguf")
    val manifest = file("src/main/assets/models/question-author-v2.json")
    inputs.files(model, manifest)
    doLast {
        check(model.isFile) { "Run tools/download_android_model.ps1 or tools/export_android_model.py before building the AI app." }
        val expected = JsonSlurper().parse(manifest) as Map<*, *>
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == expected["sha256"]) { "Question model checksum mismatch" }
    }
}
tasks.named("preBuild") { dependsOn(verifyQuestionModel) }
