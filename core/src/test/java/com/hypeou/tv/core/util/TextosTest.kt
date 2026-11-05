package com.hypeou.tv.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Normalização de nomes: é o que faz o guia encontrar o canal e o favorito
 * continuar sendo o mesmo depois que o catálogo muda de nome.
 */
class TextosTest {

    @Test
    fun `remove acento artigo e sufixo de qualidade`() {
        assertEquals("sportv 2", Textos.normalizar("SporTV 2 HD"))
        assertEquals("sportv 2", Textos.normalizar("BR - SporTV 2"))
        assertEquals("a e", Textos.normalizar("A&E"))
        assertEquals("globo", Textos.normalizar("Globo FHD"))
    }

    @Test
    fun `rotulo de grupo perde o prefixo e o emoji`() {
        assertEquals("FILMES E SÉRIES", Textos.limparRotulo("CANAIS: FILMES E SÉRIES ✨"))
        assertEquals("ESPORTES", Textos.limparRotulo("  canais : ESPORTES  "))
    }

    @Test
    fun `nome limpo tira marca de versao do acervo`() {
        assertEquals("Filme", Textos.nomeLimpo("Filme [L]"))
        assertEquals("Filme", Textos.nomeLimpo("Filme (Dublado)"))
        assertEquals("A Volta", Textos.nomeLimpo("A Volta (2020)"))
        // Uma marca que abre o título é parte do nome e fica.
        assertEquals("[REC]", Textos.nomeLimpo("[REC]"))
    }

    @Test
    fun `letra da gaveta segue a primeira letra de verdade`() {
        assertEquals("A", Textos.letraDoTitulo("(A) Fronteira"))
        assertEquals("#", Textos.letraDoTitulo("0.0MHz"))
        assertEquals("A", Textos.letraDoTitulo("Água Viva"))
        // Símbolo no começo é pulado: "¡Casa Bonita!" mora na letra C.
        assertEquals("C", Textos.letraDoTitulo("¡Casa Bonita!"))
        assertEquals("#", Textos.letraDoTitulo("123"))
    }

    @Test
    fun `gaveta especial usa a forma percentualizada do site`() {
        assertEquals("%23", Textos.gaveta("#"))
        assertEquals("A", Textos.gaveta("A"))
    }

    @Test
    fun `chaves de comparacao incluem a forma sem artigo`() {
        val chaves = Textos.chavesDe("O Auto da Compadecida")
        assertTrue(chaves.contains("auto da compadecida"))
        assertTrue(chaves.contains("o auto da compadecida"))
    }

    @Test
    fun `entidades html sao decodificadas`() {
        assertEquals("A&E", Textos.decodificarEntidades("A&amp;E"))
        assertEquals("é", Textos.decodificarEntidades("&#233;"))
    }

    @Test
    fun `mesmo nome ignora caixa acento e qualidade`() {
        assertTrue(Textos.mesmoNome("SporTV 2 HD", "BR - SporTV 2"))
        assertTrue(Textos.mesmoNome("A&E", "A & E"))
    }
}
