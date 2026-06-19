plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.shyky.tech.photo.picker.compat"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

// ★ JitPack 适配：构建 AAR 后复制到 Maven 目录并生成 POM
val pomDirPath = "maven/com/github/Shyky/PhotoPickerCompat/library-photo-picker-compat/1.0.0"

tasks.register("jitpackInstall") {
    dependsOn("assembleRelease")
    notCompatibleWithConfigurationCache("jitpackInstall 在 doLast 中写入文件，不兼容 config cache")
    doLast {
        val buildDir = project.buildDir
        val output = File(buildDir, pomDirPath)
        output.mkdirs()
        // ★ POM
        File(output, "library-photo-picker-compat-1.0.0.pom").writeText("""<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<modelVersion>4.0.0</modelVersion><groupId>com.github.Shyky.PhotoPickerCompat</groupId>
<artifactId>library-photo-picker-compat</artifactId><version>1.0.0</version><packaging>aar</packaging>
</project>""")
        // ★ AAR
        val aar = File(buildDir, "outputs/aar/library-photo-picker-compat-release.aar")
        aar.copyTo(File(output, "library-photo-picker-compat-1.0.0.aar"), overwrite = true)
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    api(libs.coil)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
