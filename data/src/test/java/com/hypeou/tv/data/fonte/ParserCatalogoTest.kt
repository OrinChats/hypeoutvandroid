package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.modelo.Serie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes dos leitores do catálogo com **trechos reais** do que o Hypeou publica.
 *
 * As amostras em `src/test/resources/catalogo` foram copiadas do site
 * (`/vod/indice.txt`, `filmes-A.txt`, `series-A.txt`, `series-A-0.txt`). Usar
 * dados reais é o que pega o tipo de erro que importa: tabulação dupla, campo
 * vazio no meio, base de servidor numerada, `#` na letra.
 *
 * Os acentos de alguns títulos aparecem como `Ãª` porque o arquivo publicado está
 * em dupla codificação. O parser não mexe em texto de título de propósito, então
 * o teste preserva o defeito em vez de escondê-lo.
 */
class ParserCatalogoTest {

    private fun recurso(caminho: String): String =
        checkNotNull(javaClass.getResourceAsStream(caminho)) { "fixture ausente: $caminho" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `indice le a base do servidor e as gavetas por letra`() {
        val indice = ParserCatalogo.indice(recurso("/catalogo/indice.txt"))

        assertTrue("o índice não pode vir vazio", !indice.vazio)
        assertEquals(1, indice.bases.size)
        assertEquals("http://dbonline.tech-cdn.top:80/", indice.bases[0])

        // Os números do arquivo publicado: 4.779 filmes e 1.165 séries na letra A.
        assertEquals(4779, indice.filmes("A"))
        assertEquals(1165, indice.series("A"))
        assertTrue("letra # precisa existir", indice.letrasComFilmes.contains("#"))
        assertTrue(indice.totalFilmes() > 4000)
    }

    @Test
    fun `filmes da letra A montam o endereco com a base numerada`() {
        val indice = ParserCatalogo.indice(recurso("/catalogo/indice.txt"))
        val filmes = ParserCatalogo.filmes(recurso("/catalogo/filmes-A.txt"), "A", indice.bases)

        assertEquals(4, filmes.size)
        assertEquals("(A) Fronteira", filmes[0].titulo)
        assertEquals("A", filmes[0].letra)
        assertEquals(
            "http://dbonline.tech-cdn.top:80/movie/ke-vin-0928392083/PZjF6XATTZ/46495.mp4",
            filmes[0].fontes.first().url,
        )

        // O sufixo [L] vira a versão "Legendado", com endereço próprio.
        val legendado = filmes.first { it.titulo.endsWith("[L]") }
        assertTrue(legendado.fontes.any { it.rotulo.contains("Legendado", ignoreCase = true) })
    }

    @Test
    fun `series da letra A trazem pedaco e numero de episodios`() {
        val series = ParserCatalogo.series(recurso("/catalogo/series-A.txt"), "A")

        assertEquals(4, series.size)
        val academia = series.first { it.titulo == "A Academia" }
        assertEquals(0, academia.pedaco)
        assertEquals(8, academia.episodios)
        assertEquals("A", academia.letra)
        assertTrue("a chave carrega título e ano", academia.chave.startsWith("s:"))
    }

    @Test
    fun `episodios de uma serie saem em ordem com endereco montado`() {
        val indice = ParserCatalogo.indice(recurso("/catalogo/indice.txt"))
        val serie = Serie(titulo = "(A)mar", ano = "", letra = "A", pedaco = 0, episodios = 15)

        val episodios = ParserCatalogo.episodios(
            recurso("/catalogo/series-A-0.txt"),
            serie,
            indice.bases,
        )

        assertEquals(5, episodios.size)
        assertEquals(1, episodios[0].temporada)
        assertEquals(1, episodios[0].numero)
        assertEquals("Principal", episodios[0].rotulo)
        assertEquals(
            "http://dbonline.tech-cdn.top:80/series/ke-vin-0928392083/PZjF6XATTZ/131187.mp4",
            episodios[0].fontes.first().url,
        )
        // A ordem publicada é a ordem da tela: T1E1, T1E2, T1E3…
        assertEquals(listOf(1, 2, 3, 4, 5), episodios.map { it.numero })
    }

    @Test
    fun `busca le nome tipo letra e ano`() {
        val linhas = "Matrix\tf\tM\t\nA Casa de Papel\ts\tA\t2017\n"
        val achados = ParserCatalogo.busca(linhas)

        assertEquals(2, achados.size)
        assertEquals("Matrix", achados[0].titulo)
        assertTrue(!achados[0].serie)
        assertEquals("M", achados[0].letra)
        assertEquals("2017", achados[1].ano)
        assertTrue(achados[1].serie)
        assertNotNull(achados[1].chave)
    }

    @Test
    fun `a letra sustenido nao pode ser confundida com comentario`() {
        // Regressão: a linha "#	415	82	0" do indice é a gaveta dos títulos que
        // começam por número ou símbolo, e os títulos "#AMFAD..." começam por "#".
        // Tratar "#" como comentário sumia com uma letra inteira do acervo.
        val indice = ParserCatalogo.indice(recurso("/catalogo/indice.txt"))
        assertTrue("a gaveta # precisa existir no índice", indice.letrasComFilmes.contains("#"))
        assertEquals(415, indice.filmes("#"))

        val filmes = ParserCatalogo.filmes(recurso("/catalogo/filmes-A.txt"), "A", indice.bases)
        assertTrue(
            "título que começa com # é conteúdo, não comentário",
            filmes.any { it.titulo.startsWith("#AMFAD") },
        )
    }

    @Test
    fun `arquivo vazio ou lixo nao derruba o leitor`() {
        assertEquals(0, ParserCatalogo.filmes("", "A", emptyList()).size)
        assertEquals(0, ParserCatalogo.series("   ", "A").size)
        assertTrue(ParserCatalogo.indice("").vazio)
        assertTrue(ParserCatalogo.busca("").isEmpty())
    }
}
