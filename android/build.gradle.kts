plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 compila Kotlin de forma integrada; esto solo fija la versión del compilador.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
