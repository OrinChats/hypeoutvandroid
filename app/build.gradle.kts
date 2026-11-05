import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Segredos do build, fora do Git.
 *
 * Nada aqui é inventado: `SUPABASE_URL` e `SUPABASE_ANON_KEY` são as mesmas
 * duas variáveis que o site do Hypeou usa para falar com o Supabase, e a chave
 * do TMDB é a pública que o cliente web já carrega. Os três valores entram por
 * `local.properties`, `chaves.properties`, propriedade `-P` ou variável de
 * ambiente — nunca escritos no código.
 *
 * Sem os valores do Supabase o aplicativo continua inteiro: o login é pulado e o
 * catálogo (que é público) abre direto. Sem a chave do TMDB, some só a capa do
 * TMDB; o resto funciona.
 */
val localProperties = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

val chavesProperties = Properties().apply {
    val arquivo = rootProject.file("chaves.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

fun segredo(nomeLocal: String, nomeChave: String = nomeLocal): String =
    (localProperties.getProperty(nomeLocal)
        ?: chavesProperties.getProperty(nomeChave)
        ?: providers.gradleProperty(nomeLocal).orNull
        ?: providers.environmentVariable(nomeLocal).orNull
        ?: "").trim()

val supabaseUrl: String = segredo("SUPABASE_URL")
val supabaseAnon: String = segredo("SUPABASE_ANON_KEY")
val tmdbKey: String = segredo("TMDB_KEY", "tmdbKey")

/**
 * A chave de assinatura fica fora do Git (`keystore.properties` + `chaves/`).
 *
 * A assinatura é a identidade do aplicativo: uma atualização por cima só
 * instala quando é a mesma chave. Perdê-la obriga todo mundo a desinstalar e
 * instalar de novo, então ela nunca entra no repositório e o build de release
 * avisa quando não a encontra.
 */
val chave = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
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
    namespace = "com.hypeou.tv"
    compileSdk = compileSdkDoProjeto()

    defaultConfig {
        // Mantido de propósito: é o mesmo applicationId da versão 2.0 já
        // publicada. Com a mesma chave de assinatura, o APK novo instala por
        // cima sem apagar favoritos, histórico e posições de quem já usa.
        applicationId = "com.orinchats.hypeoutv"

        // Android 5.0 (API 21). É o piso real das bibliotecas usadas depois dos
        // ajustes documentados em docs/COMPATIBILIDADE.md — e é o que cobre o
        // Fire TV Stick de 1ª e 2ª geração e o Basic Edition, todos em Fire OS 5
        // (Android 5.1 / API 22).
        minSdk = 21
        targetSdk = 34

        versionCode = 3
        versionName = "2.1.0"

        // Nenhum filtro de ABI: o aplicativo não traz biblioteca nativa, então
        // roda igual em ARMv7, ARM64, x86 e x86_64. Declarar filtro sem
        // biblioteca só reduziria a compatibilidade.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnon\"")
        buildConfigField("String", "TMDB_KEY", "\"$tmdbKey\"")
        buildConfigField("boolean", "TEM_SUPABASE", supabaseUrl.isNotBlank().toString())
    }

    signingConfigs {
        create("hypeou") {
            val arquivo = chave.getProperty("storeFile")
            if (arquivo != null) {
                storeFile = rootProject.file(arquivo)
                storePassword = chave.getProperty("storePassword")
                keyAlias = chave.getProperty("keyAlias")
                keyPassword = chave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 encolhe o APK e o código: abre mais rápido no TV Box.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sem keystore.properties (outra máquina) cai na chave de depuração:
            // compila e instala para teste, mas o APK publicado não sai daqui.
            signingConfig = if (chave.getProperty("storeFile") != null) {
                signingConfigs.getByName("hypeou")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Instalar o teste por cima do release (e vice-versa) sem
            // "assinaturas não conferem" quando a chave existe.
            if (chave.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("hypeou")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }

    lint {
        // O aparelho de destino é Fire TV / TV Box: lint de celular não bloqueia.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":ui"))
    implementation(project(":player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil)

    testImplementation(libs.junit)
}
