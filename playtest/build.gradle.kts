plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

// Compile the app's own sources; tiny stubs in src/main/kotlin/stubs stand in for the
// few Android-only APIs they touch (Paint, Context, BackHandler).
val app = "../app/src/main/java/com/clashclaude/game"
sourceSets {
    main {
        kotlin.srcDirs("$app/data", "$app/game", "$app/ui")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

/** Renders the home screen and a scripted battle (taps + drags) to PNGs in build/playtest. */
tasks.register<JavaExec>("screenshots") {
    group = "playtest"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("PlaytestKt")
    val out = layout.buildDirectory.dir("playtest")
    args(out.get().asFile.absolutePath)
    doFirst { out.get().asFile.mkdirs() }
}

/** Plays many headless AI-vs-bot matches and prints outcomes and sanity checks. */
tasks.register<JavaExec>("simulate") {
    group = "playtest"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("SimulateKt")
}

/** Renders each card's in-game tile to build/card-images/<id>.png (used by the Card Forge page). */
tasks.register<JavaExec>("cardImages") {
    group = "playtest"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("CardImagesKt")
    val out = layout.buildDirectory.dir("card-images")
    args(out.get().asFile.absolutePath)
}
