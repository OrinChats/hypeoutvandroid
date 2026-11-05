plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // O AGP 9 já traz o compilador Kotlin embutido. Declarar o plugin aqui, sem
    // aplicar, é o que fixa a versão do compilador usada pelos módulos — todos
    // compilam com a mesma, e nenhum build.gradle.kts de módulo precisa repetir.
    alias(libs.plugins.kotlin.android) apply false
}

/**
 * Limpeza dos artefatos, sem depender de `rm -rf` em cada máquina.
 * `./gradlew limpar` apaga só as pastas de build do projeto.
 */
tasks.register<Delete>("limpar") {
    delete(rootProject.layout.buildDirectory)
    subprojects.forEach { delete(it.layout.buildDirectory) }
}
