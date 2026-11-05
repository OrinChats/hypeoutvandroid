import java.util.Properties

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
    namespace = "com.hypeou.tv.data"
    compileSdk = compileSdkDoProjeto()

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "TMDB_KEY", "\"${chaveTmdb()}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${segredo("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${segredo("SUPABASE_ANON_KEY")}\"")
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

private fun propriedades(nome: String): Properties = Properties().apply {
    val arquivo = rootProject.file(nome)
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

/**
 * Chave do TMDB fora do código.
 *
 * Ordem: `gradle.properties` (não versionado) ou `-Phypeou.tmdbKey=...`. Sem
 * chave o build continua: o aplicativo só deixa de buscar capa e sinopse.
 */
fun chaveTmdb(): String =
    (providers.gradleProperty("hypeou.tmdbKey").orNull
        ?: runCatching { propriedades("chaves.properties").getProperty("tmdbKey") }.getOrNull()
        ?: "").trim()

/**
 * Credenciais do Supabase, exatamente como o site as usa.
 *
 * São a chave **anônima** (publicável) e o endereço do projeto — nunca a
 * `service_role`, que dá acesso total e não pode existir em um aplicativo. Sem
 * elas o login é desligado e o catálogo (público) abre direto.
 */
fun segredo(nome: String): String =
    (runCatching { propriedades("local.properties").getProperty(nome) }.getOrNull()
        ?: runCatching { propriedades("chaves.properties").getProperty(nome) }.getOrNull()
        ?: providers.gradleProperty(nome).orNull
        ?: providers.environmentVariable(nome).orNull
        ?: "").trim()

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
