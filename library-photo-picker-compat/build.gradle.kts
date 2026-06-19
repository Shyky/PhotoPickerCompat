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

val pomXml = """<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<modelVersion>4.0.0</modelVersion><groupId>com.github.Shyky.PhotoPickerCompat</groupId>
<artifactId>library-photo-picker-compat</artifactId><version>1.0.0</version><packaging>aar</packaging>
</project>"""

tasks.register("jitpackInstall") {
    dependsOn("assembleRelease")
    notCompatibleWithConfigurationCache("写入文件，不兼容 config cache")
    doLast {
        val buildDir = project.buildDir

        // ★ 1. Maven 本地仓库 (~/.m2) — JitPack 首先搜这里
        val m2Dir = File(System.getProperty("user.home"), ".m2/repository/com/github/Shyky/PhotoPickerCompat/library-photo-picker-compat/1.0.0")
        m2Dir.mkdirs()
        File(m2Dir, "library-photo-picker-compat-1.0.0.pom").writeText(pomXml)
        File(buildDir, "outputs/aar/library-photo-picker-compat-release.aar")
            .copyTo(File(m2Dir, "library-photo-picker-compat-1.0.0.aar"), overwrite = true)

        // ★ 2. build/pom.xml — JitPack 的备选搜索路径
        File(buildDir, "pom.xml").writeText(pomXml)

        // ★ 3. build/maven/... — JitPack 递归搜索的第三个路径
        val mavenDir = File(buildDir, pomDirPath)
        mavenDir.mkdirs()
        File(mavenDir, "library-photo-picker-compat-1.0.0.pom").writeText(pomXml)
        File(buildDir, "outputs/aar/library-photo-picker-compat-release.aar")
            .copyTo(File(mavenDir, "library-photo-picker-compat-1.0.0.aar"), overwrite = true)
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
