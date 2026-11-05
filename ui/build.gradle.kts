plugins {
    alias(libs.plugins.android.library)
}

/**
 * `compileSdk` do projeto: 37 por padrão.
 *
 * O runner de CI pode não ter a plataforma mais nova do SDK instalada; nesse caso
 * o workflow passa `-Phypeou.compileSdk=36` e o projeto inteiro compila contra ela.
 * Nenhuma API acima de 21 é usada no código, então compilar contra 36 ou 37 dá o
 * mesmo APK em termos de comportamento.
 */
fun compileSdkDoProjeto(): Int =
    providers.gradleProperty("hypeou.compileSdk").orNull?.toIntOrNull() ?: 37
android {
    namespace = "com.hypeou.tv.ui"
    compileSdk = compileSdkDoProjeto()

    defaultConfig {
        // Piso do aplicativo: Android 5.0. Ver docs/COMPATIBILIDADE.md.
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core"))
    api(libs.androidx.appcompat)
    api(libs.androidx.recyclerview)
    api(libs.coil)
    implementation(libs.kotlinx.coroutines.android)
}
