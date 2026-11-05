package com.hypeou.tv.data.fonte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do leitor da lista de canais com uma amostra no formato real do
 * `canais.m3u8` publicado (atributos `tvg-*`, `group-title` com "CANAIS:" e
 * emoji, um canal por linha de endereço).
 *
 * A amostra em `src/test/resources/canais/canais.m3u8` mantém a acentuação
 * correta — o arquivo publicado está em dupla codificação, e testar o defeito de
 * codificação não diria nada sobre o parser.
 */
class ParserCanaisTest {

    private val lista: List<com.hypeou.tv.core.modelo.Canal> by lazy {
        val texto = checkNotNull(javaClass.getResourceAsStream("/canais/canais.m3u8"))
            .bufferedReader()
            .use { it.readText() }
        ParserCanais.ler(texto)
    }

    @Test
    fun `le todas as entradas da lista`() {
        assertEquals(8, lista.size)
        assertTrue("todo canal precisa de pelo menos um endereço", lista.all { it.fontes.isNotEmpty() })
        assertTrue("todo endereço é http(s)", lista.all { c -> c.fontes.all { it.url.startsWith("http") } })
    }

    @Test
    fun `limpa o prefixo CANAIS e o emoji do grupo`() {
        val ae = lista.first { it.nome == "A&E" }
        assertEquals("FILMES E SÉRIES", ae.categoria)

        val sportv = lista.first { it.nome == "SporTV 2" }
        assertEquals("ESPORTES", sportv.categoria)

        val globo = lista.first { it.nome == "Globo SP" }
        assertEquals("ABERTOS", globo.categoria)
        assertTrue("nenhuma categoria pode manter o prefixo cru", lista.none { it.categoria.startsWith("CANAIS") })
    }

    @Test
    fun `guarda logo e chave normalizada para casar com o guia`() {
        val ae = lista.first { it.nome == "A&E" }
        assertTrue(ae.logo.orEmpty().contains("A%2526E.png"))
        assertEquals("a e", ae.chaveGuia)
        assertEquals("sportv 2", lista.first { it.nome == "SporTV 2" }.chaveGuia)
    }

    @Test
    fun `canal sem logo continua valido`() {
        val radio = lista.first { it.nome == "Rádio Globo" }
        assertNull(radio.logo)
        assertEquals("RÁDIOS", radio.categoria)
        assertEquals(1, radio.fontes.size)
    }

    @Test
    fun `a mesma qualidade repetida nao duplica endereco`() {
        val texto = """
            #EXTM3U
            #EXTINF:-1 group-title="CANAIS: ABERTOS",Globo SP
            http://exemplo/1.ts
            #EXTINF:-1 group-title="CANAIS: ABERTOS",Globo SP
            http://exemplo/1.ts
            #EXTINF:-1 group-title="CANAIS: ABERTOS",Globo SP
            http://exemplo/2.ts
        """.trimIndent()

        val canais = ParserCanais.ler(texto)
        assertEquals(1, canais.size)
        assertEquals(2, canais[0].fontes.size)
    }

    @Test
    fun `lista dentro de pagina html e recuperada`() {
        val texto = """
            <html><body><pre>
            #EXTM3U
            #EXTINF:-1 group-title="CANAIS: ABERTOS",SBT
            http://exemplo/sbt.ts
            </pre></body></html>
        """.trimIndent()

        val canais = ParserCanais.ler(texto)
        assertEquals(1, canais.size)
        assertEquals("SBT", canais[0].nome)
        assertNotEquals(0, canais[0].fontes.size)
    }

    @Test
    fun `texto vazio devolve lista vazia`() {
        assertTrue(ParserCanais.ler("").isEmpty())
        assertTrue(ParserCanais.ler("#EXTM3U\n").isEmpty())
    }
}
