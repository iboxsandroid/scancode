import java.util.Locale

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
//    id("maven-publish")
//    alias(libs.plugins.jetbrainsKotlinAndroidExtensions)
}

android {
    namespace = "com.itgz8.scancode"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    viewBinding {
        enable = true
    }
    libraryVariants.all {
        val variantName = name
        outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                this.outputFileName = "your_custom_aar_name-$variantName.aar"
            }
        }
    }
}

// 启用 AAR 打包
// afterEvaluate {
//    // build.gradle.kts (Module)
// publishing {
//     publications {
//         create<MavenPublication>("release") {
//             // 从组件中获取aar/jar
//             from(components["release"])
            
//             // 配置Maven元数据
//             groupId = "com.itgz8"
//             artifactId = "scancode"
//             version = "1.0.0"
            
//             // 添加Pom文件描述
//             pom {
//                 name.set("ScanCode")
//                 description.set("A useful Android library")
//                 url.set("https://github.com/example/mylibrary")
                
//                 licenses {
//                     license {
//                         name.set("The Apache License, Version 2.0")
//                         url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
//                     }
//                 }
                
//                 developers {
//                     developer {
//                         id.set("developer")
//                         name.set("Developer Name")
//                         email.set("developer@example.com")
//                     }
//                 }
                
//                 scm {
//                     connection.set("scm:git:git://github.com/example/mylibrary.git")
//                     developerConnection.set("scm:git:ssh://github.com/example/mylibrary.git")
//                     url.set("https://github.com/example/mylibrary")
//                 }
//             }
//         }
//     }
    
//     // 配置仓库地址（示例为本地Maven）
//     repositories {
//         maven {
//             name = "sonatype"
//             url = uri(
//                 if (version.toString().endsWith("SNAPSHOT")) 
//                     "https://s01.oss.sonatype.org/content/repositories/snapshots/"
//                 else 
//                     "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
//             )
//             credentials {
//                 username = sonatypeUsername
//                 password = sonatypePassword
//             }
//         }
//     }
// }

// }

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.barcode.scanning)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}