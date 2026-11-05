import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Um só lugar de repositórios: nenhum módulo declara os seus.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hypeou TV"

/**
 * Quatro camadas e o aplicativo. Cada uma só enxerga a de baixo:
 *
 * - `core`   — modelos, preferências, histórico, favoritos, rede e utilidades.
 *              Não conhece tela, catálogo nem player.
 * - `data`   — leitura do catálogo publicado (M3U, VOD, guia), TMDB e login.
 *              Depende só do `core`.
 * - `ui`     — cartões, fileiras, grades, tema e foco de controle remoto.
 *              Depende do `core`; não conhece regra de negócio.
 * - `player` — motor de reprodução Media3, reconexão e faixas.
 *              Depende do `core`.
 * - `app`    — telas, navegação e ciclo de vida. Depende de todos.
 *
 * As telas de TV ao vivo, filmes, séries, animes, busca, favoritos, histórico
 * e ajustes vivem em `app` como páginas de `InicioActivity`: são todas a mesma
 * moldura (trilha lateral + conteúdo + D-pad), e separá-las em módulos custaria
 * nove manifestos e nove cópias da mesma navegação sem ganhar isolamento real.
 */
include(
    ":app",
    ":core",
    ":data",
    ":ui",
    ":player",
)
