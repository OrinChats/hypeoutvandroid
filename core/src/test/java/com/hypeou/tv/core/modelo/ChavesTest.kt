package com.hypeou.tv.core.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As chaves de conteúdo são a identidade de favoritos e de "continuar
 * assistindo": elas precisam sobreviver ao catálogo mudar de ordem e não podem
 * confundir "A Volta" com "A Volta (2020)".
 */
class ChavesTest {

    @Test
    fun `filme e serie nao colidem`() {
        val filme = chaveDe(TipoMidia.FILME, "A Volta")
        val serie = chaveDe(TipoMidia.SERIE, "A Volta", "2020")

        assertTrue(filme.startsWith("f:"))
        assertTrue(serie.startsWith("s:"))
        assertTrue(filme != serie)
    }

    @Test
    fun `mesmo titulo com anos diferentes sao chaves diferentes`() {
        val uma = chaveDe(TipoMidia.SERIE, "A Volta", "2020")
        val outra = chaveDe(TipoMidia.SERIE, "A Volta", "1998")

        assertTrue(uma != outra)
    }

    @Test
    fun `desmembrar devolve titulo e ano`() {
        val chave = chaveDe(TipoMidia.SERIE, "A Casa de Papel", "2017")
        val (tipo, titulo, ano) = checkNotNull(desmembrarChave(chave))

        assertEquals(TipoMidia.SERIE, tipo)
        assertEquals("A Casa de Papel", titulo)
        assertEquals("2017", ano)
    }

    @Test
    fun `desmembrar filme nao inventa ano`() {
        val (tipo, titulo, ano) = checkNotNull(desmembrarChave(chaveDeFilme("Matrix")))

        assertEquals(TipoMidia.FILME, tipo)
        assertEquals("Matrix", titulo)
        assertEquals("", ano)
    }

    @Test
    fun `chave invalida devolve nulo`() {
        assertNull(desmembrarChave(""))
        assertNull(desmembrarChave("sem dois pontos"))
        assertNull(desmembrarChave("x:algo"))
        assertNull(desmembrarChave("f:"))
    }

    @Test
    fun `de chave para achado recria a letra da gaveta`() {
        val chave = chaveDeSerie("Água Viva", "1980")
        val achado = checkNotNull(deChaveParaAchado(chave))

        assertEquals("Água Viva", achado.titulo)
        assertTrue(achado.serie)
        assertEquals("A", achado.letra)
        assertEquals("1980", achado.ano)
        assertEquals(chave, achado.chave)
    }

    @Test
    fun `progresso de episodio nao colide com o da serie`() {
        val serie = chaveDeSerie("A Academia", "2020")
        val episodio = chaveDoEpisodio("A Academia", "2020", 1, 2)

        assertTrue(episodio != serie)
        // A chave do episódio começa pelo título, não pela chave da série.
        assertTrue(episodio.startsWith("A Academia"))
        assertTrue(episodio.endsWith("T1\u0000E2"))
    }

    @Test
    fun `retomada calcula progresso e nome`() {
        val retomada = Retomada(
            chave = "s:x",
            titulo = "Série",
            tipo = TipoMidia.SERIE,
            letra = "S",
            posicaoMs = 1_800_000L,
            duracaoMs = 3_600_000L,
            temporada = 2,
            episodio = 5,
        )

        assertEquals(0.5f, retomada.progresso, 0.001f)
        assertEquals("Série · T2E5", retomada.nomeCompleto)
    }

    @Test
    fun `programa sabe se esta no ar`() {
        val programa = Programa("Jornal", inicio = 1_000L, fim = 2_000L)

        assertTrue(programa.noAr(1_500L))
        assertTrue(!programa.noAr(2_000L))
        assertEquals(0.5f, programa.progresso(1_500L), 0.001f)
        assertEquals(1_000L, programa.duracaoMs)
    }
}
