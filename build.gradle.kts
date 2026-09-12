plugins {
    id("com.android.application") version "8.7.2" apply false
    // RootEncoder 2.8.1 (JitPack) arrasta kotlin-stdlib 2.3.x: o consumidor precisa de Kotlin >= 2.3.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
