package com.hypeou.tv.data.fonte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ordem das categorias decide o que aparece primeiro na TV ao vivo.
 *
 * A lista publicada agrupa por emissora ("CANAIS: HBO"), e ordem alfabética
 * pura poria "A FAZENDA 18" na frente de "ABERTOS".
 */
class OrdemCategoriasTest {

    @Test
    fun `aberta antes de filmes antes de esportes`() {
        val ordenadas = OrdemCategorias.ordenar(listOf("ESPORTES", "FILMES", "ABERTOS"))

        assertEquals(listOf("ABERTOS", "FILMES", "ESPORTES"), ordenadas)
    }

    @Test
    fun `outros vai sempre para o fim`() {
        val ordenadas = OrdemCategorias.ordenar(listOf("Outros", "NOTÍCIAS", "INFANTIL"))

        assertEquals("NOTÍCIAS", ordenadas.first())
        assertEquals("Outros", ordenadas.last())
    }

    @Test
    fun `rotulos repetidos nao duplicam categoria`() {
        val ordenadas = OrdemCategorias.ordenar(listOf("ESPORTES", "ESPORTES", "ESPORTES"))

        assertEquals(1, ordenadas.size)
    }

    @Test
    fun `adulto e radio ficam depois do conteudo comum`() {
        val ordenadas = OrdemCategorias.ordenar(listOf("ADULTOS", "RÁDIOS", "ABERTOS"))

        assertEquals("ABERTOS", ordenadas.first())
        assertTrue(ordenadas.indexOf("RÁDIOS") < ordenadas.indexOf("ADULTOS"))
    }
}
