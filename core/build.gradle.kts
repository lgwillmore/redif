plugins {
  id("buildsrc.convention.kotlin-jvm")
  id("buildsrc.convention.publish")
  `java-test-fixtures`
}

description =
    "Redif core: the Redis facade, its client configuration and the in-memory implementation"

dependencies {
  api(libs.kotlinxCoroutines)

  // The contract every implementation is tested against; not published.
  testFixturesApi(kotlin("test-junit5"))
  testFixturesApi(libs.assertk)
  testFixturesApi(libs.kotlinxCoroutines)
}
