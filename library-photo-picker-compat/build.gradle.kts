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

// ★ 构建完成后将 AAR 复制到本地共享仓库目录
tasks.register<Copy>("publishLocal") {
    dependsOn("assembleRelease")
    val outputDir = layout.buildDirectory.dir("outputs/aar").get().asFile
    from(outputDir) {
        include("*-release.aar")
        rename { "library-photo-picker-compat-1.0.0.aar" }
    }
    into(file("$rootDir/../.local-maven-repo/com/github/Shyky/PhotoPickerCompat/library-photo-picker-compat/1.0.0"))
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
