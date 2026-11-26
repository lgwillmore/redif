plugins {
  id("buildsrc.convention.kotlin-jvm")
  id("buildsrc.convention.publish")
}

description = "Redif Lettuce client: the facade over a Redis instance through io.lettuce"

dependencies {
  // Core types appear in this module's public API.
  api(project(":redif-core"))
  implementation(libs.lettuceCore)
  implementation(libs.kotlinxCoroutinesReactive)

  testImplementation(project(":redif-compatibility"))
}
