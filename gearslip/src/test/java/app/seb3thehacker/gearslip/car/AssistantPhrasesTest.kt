package app.seb3thehacker.gearslip.car

import app.seb3thehacker.gearslip.speech.SpeechModels.Language
import app.seb3thehacker.gearslip.speech.SpeechModels.Model
import app.seb3thehacker.gearslip.speech.SpeechModels.Role
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPhrasesTest {
    private fun en(raw: String) = AssistantCommands.natural(raw, EnglishPhrases)
    private fun es(raw: String) = AssistantCommands.natural(raw, SpanishPhrases)
    private fun starts(text: String, triggers: List<String>) = triggers.any { text.startsWith(it) }

    @Test
    fun englishFillersStillGo() {
        assertEquals("call mom", en("Hey Gearslip, call Mom please."))
        assertEquals("navigate to the airport", en("Can you navigate to the airport, thanks"))
    }

    @Test
    fun englishCommandsStillMatch() {
        assertTrue(starts(en("Navigate to the nearest gas station"), EnglishPhrases.navigateTriggers))
        assertTrue(starts(en("Play Daft Punk"), EnglishPhrases.playTriggers))
        assertTrue(starts(en("Open Spotify"), EnglishPhrases.openTriggers))
        assertTrue(EnglishPhrases.isPause(en("Pause")))
        assertTrue(EnglishPhrases.isNext(en("Skip")))
        assertTrue(EnglishPhrases.isWhatsPlaying(en("What's playing?")))
        assertTrue(EnglishPhrases.isHome(en("Go home")))
        assertTrue(EnglishPhrases.isTime(en("What time is it?")))
        assertTrue(EnglishPhrases.isWeather(en("Is it going to rain?")))
        assertTrue(EnglishPhrases.isHelp(en("What can I say?")))
        assertTrue(en("Exit") in EnglishPhrases.exitWords)
    }

    @Test
    fun englishCallsLoseWhichPhone() {
        assertEquals("mom", EnglishPhrases.giveACall.find(en("Give Mom a call"))?.groupValues?.get(1))
        assertEquals("call mom", en("call mom on her mobile").replace(EnglishPhrases.whichPhone, ""))
    }

    @Test
    fun calendarIsNotTakenForTheNextSong() {
        assertTrue(EnglishPhrases.isCalendar(en("What's on my calendar?")))
        assertTrue(EnglishPhrases.isCalendar(en("Next event")))
        assertFalse(EnglishPhrases.isCalendar(en("Next song")))
        assertTrue(SpanishPhrases.isCalendar(es("¿Cuál es mi próximo evento?")))
        assertFalse(SpanishPhrases.isCalendar(es("Siguiente canción")))
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
        assertTrue(starts(es("Ve a ajustes"), SpanishPhrases.goTo))
        assertTrue(SpanishPhrases.isCalendar(es("¿Qué tengo hoy?")))
        assertTrue(SpanishPhrases.isWhatsPlaying(es("¿Qué suena?")))
        assertTrue(SpanishPhrases.isTime(es("¿Qué hora es?")))
        assertTrue(SpanishPhrases.isWeather(es("¿Qué tiempo hace?")))
        assertTrue(SpanishPhrases.isSettings(es("Abre los ajustes")))
        assertTrue(SpanishPhrases.isHome(es("Vuelve al inicio")))
        assertTrue(SpanishPhrases.isHelp(es("Ayuda")))
        assertTrue(es("Salir") in SpanishPhrases.exitWords)
    }

    @Test
    fun spanishNamesTheAppFirst() {
        val m = SpanishPhrases.appFirst.find(es("Usa Organic Maps para ir a Madrid"))
        assertEquals("organic maps", m?.groupValues?.get(1))
        assertTrue(starts(m!!.groupValues[2], SpanishPhrases.navigateTriggers))
    }

    @Test
    fun spanishCallsLoseWhichPhone() {
        assertEquals("mama", SpanishPhrases.giveACall.find(es("Dale un toque a mamá"))?.groupValues?.get(1))
        assertEquals("llama a mama", es("Llama a mamá al móvil").replace(SpanishPhrases.whichPhone, ""))
    }

    @Test
    fun spanishMusicControlsAreNotSearches() {
        assertTrue("la siguiente" in SpanishPhrases.resumeWords)
        assertTrue(SpanishPhrases.isNext(es("Pon la siguiente")))
        assertTrue(SpanishPhrases.isPause(es("Pausa")))
        assertTrue(SpanishPhrases.isResume(es("Sigue")))
        assertTrue(SpanishPhrases.isPrevious(es("La anterior")))
    }

    @Test
    fun howLongIsNotTheWeather() {
        assertFalse(SpanishPhrases.isWeather(es("¿Cuánto tiempo queda?")))
    }

    @Test
    fun bothLanguagesListTheSameHelp() {
        assertEquals(EnglishPhrases.helpCommands.size, SpanishPhrases.helpCommands.size)
    }

    @Test
    fun everyLanguageHasALiveAndAFinalModel() {
        for (language in Language.entries) {
            val recommended = Model.entries.filter { it.recommended && language in it.languages }
            assertEquals(language.name, 1, recommended.count { it.role == Role.LIVE })
            assertEquals(language.name, 1, recommended.count { it.role == Role.FINAL })
        }
        assertTrue(Model.entries.all { it.languages.isNotEmpty() })
    }

    @Test
    fun localeKeepsThePhonesVariant() {
        val phone = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-AR"))
            assertEquals("es-AR", Language.SPANISH.locale.toLanguageTag())
            assertEquals("en-US", Language.ENGLISH.locale.toLanguageTag())
            Locale.setDefault(Locale.forLanguageTag("en-GB"))
            assertEquals("es-ES", Language.SPANISH.locale.toLanguageTag())
        } finally {
            Locale.setDefault(phone)
        }
    }
}
