import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  android {
    namespace = "ldv.shuuen.bass"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    withJava()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  sourceSets {
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }

    jvmMain.dependencies {
      implementation(libs.jna)
    }
  }
}
