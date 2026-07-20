import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.koin.compiler)
  alias(libs.plugins.jetbrains.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room3)
}

kotlin {
  jvm()

  android {
    namespace = "ldv.shuuen.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
    androidResources {
      enable = true
    }
    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.activity.compose)
    }
    commonMain.dependencies {
      api(projects.bass)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)

      // room
      implementation(libs.androidx.room3.runtime)
      implementation(libs.androidx.sqlite.bundled)

      // koin
      implementation(project.dependencies.platform(libs.koin.bom))
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.navigation3)
      implementation(libs.koin.annotations)

      // navigation
      implementation(libs.androidx.navigation3.ui)
      implementation(libs.androidx.lifecycle.viewmodel.navigation3)
      implementation(libs.androidx.material3.adaptive.navigation3)

      // etc
      implementation(libs.kotlinx.serialization.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.napier)
      implementation(libs.kstore)
      implementation(libs.kstore.file)

      // native file picker (desktop + android)
      implementation(libs.filekit.dialogs.compose)

      // to review
      implementation(compose.materialIconsExtended)
    }
    jvmMain.dependencies {
      implementation(libs.appdirs)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutinesTest)
    }
  }
}

dependencies {
  androidRuntimeClasspath(libs.compose.uiTooling)
  add("kspAndroid", libs.androidx.room3.compiler)
  add("kspJvm", libs.androidx.room3.compiler)
}

room3 {
  schemaDirectory("$projectDir/schemas")
}