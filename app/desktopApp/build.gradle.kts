import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

dependencies {
  implementation(projects.shared)

  implementation(compose.desktop.currentOs)
  implementation(libs.compose.components.resources)
  implementation(libs.kotlinx.coroutinesSwing)

  implementation(libs.napier)

  implementation(libs.compose.uiToolingPreview)

  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.junit)
}

compose.resources {
  packageOfResClass = "ldv.shuuen.desktop.generated.resources"
}

compose.desktop {
  application {
    mainClass = "ldv.shuuen.MainKt"

    buildTypes.release.proguard.isEnabled.set(false)

    nativeDistributions {
      appResourcesRootDir.set(project.layout.projectDirectory.dir("src/main/appResources"))
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "ldv.shuuen"
      packageVersion = "1.0.0"

      windows {
        iconFile.set(project.file("src/main/resources/icons/shuuen.ico"))
      }
      macOS {
        iconFile.set(project.file("src/main/resources/icons/shuuen.icns"))
      }
      linux {
        iconFile.set(project.file("src/main/resources/icons/shuuen.png"))
      }
    }
  }
}
