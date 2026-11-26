plugins {
  id("buildsrc.convention.kotlin-jvm")
  id("buildsrc.convention.publish")
}

description = "Redif core: the Redis facade, its command groups and the client configuration"

dependencies { api(libs.kotlinxCoroutines) }
