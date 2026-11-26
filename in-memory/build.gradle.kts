plugins {
  id("buildsrc.convention.kotlin-jvm")
  id("buildsrc.convention.publish")
}

description = "Redif in-memory: the facade kept in the process, with no Redis to run"

dependencies {
  // Core types appear in this module's public API.
  api(project(":redif-core"))

  testImplementation(project(":redif-compatibility"))
}
