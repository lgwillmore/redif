plugins {
  id("buildsrc.convention.kotlin-jvm")
  id("buildsrc.convention.publish")
}

description =
    "Redif compatibility: the contract suites every implementation of the facade is tested against"

dependencies {
  // The suites are test classes an implementation's own tests extend, so what they are written
  // with is part of this module's API.
  api(project(":redif-core"))
  api(kotlin("test-junit5"))
  api(libs.assertk)
  api(libs.kotlinxCoroutines)
}
