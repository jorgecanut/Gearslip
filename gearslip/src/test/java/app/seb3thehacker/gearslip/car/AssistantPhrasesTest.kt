package app.seb3thehacker.gearslip.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPhrasesTest {
    private fun es(raw: String) = AssistantCommands.natural(raw, SpanishPhrases)
    private fun starts(text: String, triggers: List<String>) = triggers.any { text.startsWith(it) }

    @Test
    fun englishFillersStillGo() {
        assertEquals("call mom", AssistantCommands.natural("Hey Gearslip, call Mom please.", EnglishPhrases))
    }

    @Test
    fun spanishLosesFillersAccentsAndPunctuation() {
        assertEquals("llamar a jose", es("Oye, ¿puedes llamar a José?"))
        assertEquals("llevame a la gasolinera mas cercana", es("Llévame a la gasolinera más cercana, por favor"))
    }

    @Test
    fun spanishCommandsFindTheirTriggers() {
        assertTrue(starts(es("Llama a mamá"), SpanishPhrases.callTriggers))
        assertTrue(starts(es("Llévame al aeropuerto"), SpanishPhrases.navigateTriggers))
        assertTrue(starts(es("Pon Rosalía"), SpanishPhrases.playTriggers))
        assertTrue(starts(es("Abre Spotify"), SpanishPhrases.openTriggers))
        assertTrue(SpanishPhrases.isCalendar(es("¿Qué tengo hoy?")))
        assertTrue(SpanishPhrases.isWhatsPlaying(es("¿Qué suena?")))
        assertTrue(SpanishPhrases.isTime(es("¿Qué hora es?")))
        assertTrue(SpanishPhrases.isWeather(es("¿Qué tiempo hace?")))
        assertTrue(SpanishPhrases.isHelp(es("Ayuda")))
    }

    @Test
    fun spanishMusicControlsAreNotSearches() {
        assertTrue("la siguiente" in SpanishPhrases.resumeWords)
        assertTrue(SpanishPhrases.isNext(es("Pon la siguiente")))
        assertTrue(SpanishPhrases.isPause(es("Pausa")))
    }

    @Test
    fun howLongIsNotTheWeather() {
        assertFalse(SpanishPhrases.isWeather(es("¿Cuánto tiempo queda?")))
    }
}
