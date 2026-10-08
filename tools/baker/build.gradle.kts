plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":engine"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("baker.BakeKt")
}

/**
 * Renders every model to sprite sheets in app/src/main/assets/sprites and regenerates
 * app/.../ui/SpriteManifest.kt. Run with `./gradlew :baker:bake` (optionally -Ponly=knight,giant).
 */
tasks.register<JavaExec>("bake") {
    group = "art"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("baker.BakeKt")
    workingDir = rootProject.projectDir
    jvmArgs("-Xmx2g", "-Djava.awt.headless=true")
    (project.findProperty("only") as String?)?.let { args("--only", it) }
}
