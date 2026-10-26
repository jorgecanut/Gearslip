package app.seb3thehacker.gearslip.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.seb3thehacker.gearslip.media.CarMedia
import app.seb3thehacker.gearslip.speech.SpeechModels
import app.seb3thehacker.gearslip.speech.VoiceFocus
import java.text.DateFormat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns what [CarAssistant] heard into an action plus something to say back.
 *
 * Deliberately small and keyword-matched rather than run through any NLU or bundled model: a
 * fixed set of things a driver actually wants hands-free (media, calling, opening an app,
 * getting back Home), covering the same ground the rest of the car UI already does rather than
 * trying to be a general assistant. The words themselves, heard and said, are in
 * [AssistantPhrases], one set per language.
 */
object AssistantCommands {

    private val phrases get() = AssistantPhrases.current

    suspend fun handle(context: Context, navigator: CarNavigator, frame: CarEnvironment.Frame, heardRaw: String): String {
        val heard = natural(heardRaw, phrases)
        VoiceHelp.hide()
        if (phrases.isHelp(heard)) {
            VoiceHelp.show()
            return phrases.helpShown
        }
        if (heard.matches(Regex("(fuck|f\\*+) (you|off)( too)?"))) return "Fuck you too."

        navigateCommand(context, navigator, frame, heard)?.let { return it }
        playSearchCommand(context, navigator, heard)?.let { return it }
        // Before the media ones, which take "next event" for "next".
        calendarCommand(context, heard)?.let { return it }
        mediaCommand(heard)?.let { return it }
        navCommand(navigator, heard)?.let { return it }
        infoCommand(heard)?.let { return it }
        callCommand(context, heard)?.let { return it }
        openAppCommand(context, navigator, frame, heard)?.let { return it }

        return phrases.unknown
    }

    /**
     * What was said, minus the padding people wrap a command in: "hey", "can you", "I want to",
     * "please", "thanks". Each command below then only has to know its own words.
     */
    internal fun natural(raw: String, phrases: AssistantPhrases): String {
        var t = fold(raw).replace(Regex("[.,!?¿¡]"), " ").replace(Regex("\\s+"), " ").trim()
        var changed = true
        while (changed) {
            changed = false
            for (lead in phrases.leadFillers) if (t.startsWith("$lead ")) { t = t.removePrefix("$lead ").trim(); changed = true }
            for (tail in phrases.tailFillers) if (t.endsWith(" $tail")) { t = t.removeSuffix(" $tail").trim(); changed = true }
        }
        return t
    }

    /** Lowercase and without accents, so "llévame" matches "llevame" and "jose" finds José. */
    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}"), "")

    /** The part after the first of [triggers] that [text] starts with, or null. */
    private fun after(text: String, triggers: List<String>): String? =
        triggers.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it).trim() }

    /** [name] without "the", "my" or "app" around it. */
    private fun appName(name: String): String {
        var t = name
        for (lead in phrases.nameLeads) t = t.removePrefix(lead)
        for (tail in phrases.nameTails) t = t.removeSuffix(tail)
        return t.trim()
    }

    private suspend fun allEntries(context: Context): List<Entry> {
        val vehicleData = CarSettings.experimentalFeaturesEnabled.value && CarSettings.carSensorsEnabled.value
        val builtIn = BuiltInApps.entries.filter { it.builtIn !== BuiltInApps.VehicleData || vehicleData }
        return builtIn + (LauncherCache.entries ?: withContext(Dispatchers.IO) { LauncherCache.load(context) })
    }

    /**
     * Best label match: exact, then a label starting with [name], then one containing it. Spaces
     * and punctuation don't count, since the recognizer may hear "MapQuest" as "map quest".
     */
    private fun <T> bestMatch(items: List<T>, name: String, label: (T) -> String): T? {
        val n = squash(appName(name))
        if (n.isEmpty()) return null
        items.firstOrNull { squash(label(it)) == n }?.let { return it }
        // Loose matches need a real word: "pizza in a" mustn't pick "Organic Maps" for its "a".
        if (n.length < 3) return null
        return items.firstOrNull { squash(label(it)).startsWith(n) }
            ?: items.firstOrNull { squash(label(it)).contains(n) }
    }

    private fun squash(s: String) = fold(s).filter { it.isLetterOrDigit() }

    /**
     * "navigate to X [with|using|on|via Y]" - Y picks the nav app by (partial) label; without it,
     * whichever nav app was used last, or the only one installed, or the first one found. Starts
     * it straight into navigating there via [CarAppConnection]'s `ACTION_NAVIGATE` deep link -
     * the same one Android Auto's own Assistant integration uses - rather than opening the app
     * and leaving the driver to type the address in on the car keyboard.
     */
    private suspend fun navigateCommand(
        context: Context,
        navigator: CarNavigator,
        frame: CarEnvironment.Frame,
        heard: String,
    ): String? {
        // "use Organic Maps to get to X", "open OsmAnd and navigate to X": the app comes first.
        var text = heard
        var leadingApp: String? = null
        phrases.appFirst.find(text)?.let { m ->
            if (after(m.groupValues[2], phrases.navigateTriggers) != null) {
                leadingApp = m.groupValues[1]
                text = m.groupValues[2]
            }
        }
        var destination = after(text, phrases.navigateTriggers) ?: return null
        if (destination.isBlank()) return phrases.whereTo

        val entries = allEntries(context)
        // "go to Spotify" means the app, and "go to settings" the screen; "go to Target" is a place.
        val asApp = appName(destination)
        if (phrases.goTo.any { text.startsWith(it) } &&
            (asApp in phrases.screenWords || asApp in phrases.appNicknames || entries.any { fold(it.label) == asApp })
        ) {
            return null
        }
        val navApps = entries.filter { it.template?.isNavigation == true }
        if (navApps.isEmpty()) return phrases.noNavApps

        // "X with Organic Maps", "X on OsmAnd": split only when the end names a nav app, so a
        // place like "Starbucks on Main Street" stays whole.
        var chosenApp = leadingApp?.let { name -> bestMatch(navApps, name) { it.label } ?: return phrases.noNavAppCalled(name) }
        if (chosenApp == null) {
            for (sep in phrases.navAppSeparators) {
                val at = destination.lastIndexOf(sep)
                if (at <= 0) continue
                val app = bestMatch(navApps, destination.substring(at + sep.length)) { it.label } ?: continue
                chosenApp = app
                destination = destination.substring(0, at).trim()
                break
            }
        }
        // "the nearest gas station" -> "gas station": the map app already searches around the car
        // and AutoRoute picks the nearest result, and Organic Maps took "nearest" as part of the name.
        val (place, nearby) = plainPlace(destination)
        destination = place
        if (destination.isBlank()) return phrases.whereTo

        val chosen = chosenApp ?: when {
            navApps.size == 1 -> navApps.first()
            else -> navApps.firstOrNull { it.componentId == CarSettings.lastNav.value } ?: navApps.first()
        }
        val template = chosen.template ?: return phrases.notNavApp

        CarServices.connectNav(template, frame, destination)
        navigator.home()
        return if (nearby) phrases.findingNearest(destination, chosen.label) else phrases.navigating(destination, chosen.label)
    }

    /** [destination] without "a", "the nearest", "near me" and the like, and whether any of that said "close by". */
    private fun plainPlace(destination: String): Pair<String, Boolean> {
        var t = destination
        var nearby = false
        var changed = true
        while (changed) {
            changed = false
            for (lead in phrases.placeLeads) if (t.startsWith(lead)) {
                t = t.removePrefix(lead).trim(); changed = true
                if (lead.trim() !in phrases.articles) nearby = true
            }
            for (tail in phrases.placeTails) if (t.endsWith(tail)) { t = t.removeSuffix(tail).trim(); changed = true; nearby = true }
        }
        return t to nearby
    }

    /**
     * "play X" with something after it: a song, artist, album or playlist, asked of the music app
     * that's open through its own search ([CarMedia.search]). A bare "play", or "play music",
     * falls through to resume.
     */
    private suspend fun playSearchCommand(context: Context, navigator: CarNavigator, heard: String): String? {
        var query = after(heard, phrases.playTriggers) ?: return null
        for (lead in phrases.playLeads) query = query.removePrefix(lead).trim()
        if (query.isEmpty() || query in phrases.resumeWords) return null
        val media = CarServices.media

        // "play Daft Punk on Metrolist": switch to that app first, if it's one we know.
        for (sep in phrases.musicAppSeparators) {
            val at = query.lastIndexOf(sep)
            if (at <= 0) continue
            val apps = withContext(Dispatchers.IO) { app.seb3thehacker.gearslip.media.MediaCatalog.installed(context) }
            val wanted = bestMatch(apps, query.substring(at + sep.length)) { it.label } ?: continue
            query = query.substring(0, at).trim()
            if (query.isEmpty() || query in phrases.resumeWords) {
                CarServices.openMedia(wanted, autoplay = true)
                navigator.media()
                return phrases.opening(wanted.label)
            }
            if (CarServices.mediaApp.value?.component != wanted.component) {
                CarServices.openMedia(wanted)
                navigator.media()
                // Give it a moment to connect before asking it to search.
                withTimeoutOrNull(APP_CONNECT_MS) {
                    while (media.appLabel == null || (!media.canSearchLibrary && !media.now.value.canSearch)) delay(200)
                }
            }
            break
        }

        // No music app open, or its connection is gone (the app was closed): use the one played
        // last, the way "play X" works on Android Auto.
        val phase = media.phase.value
        if (media.appLabel == null || phase == CarMedia.Phase.IDLE || phase == CarMedia.Phase.REJECTED) {
            if (!CarServices.reopenLastMedia()) return phrases.openMusicFirst
            navigator.media()
        }
        withTimeoutOrNull(APP_CONNECT_MS) {
            while (media.phase.value != CarMedia.Phase.READY || media.appLabel == null ||
                (!media.canSearchLibrary && !media.now.value.canSearch)
            ) delay(200)
        }
        val app = media.appLabel ?: return phrases.musicAppTooSlow
        if (!media.canSearchLibrary && !media.now.value.canSearch) {
            return phrases.ignoresSearch(app)
        }
        media.playBestMatch(query)
        return phrases.lookingFor(query, app)
    }

    private fun mediaCommand(heard: String): String? {
        val media = CarServices.media
        return when {
            phrases.isPause(heard) -> {
                media.pause()
                VoiceFocus.afterRelease { media.pause() }
                phrases.paused
            }
            phrases.isResume(heard) -> {
                if (!media.now.value.playing) media.togglePlay()
                phrases.playing
            }
            phrases.isNext(heard) -> {
                media.next()
                phrases.skipping
            }
            phrases.isPrevious(heard) -> {
                media.previous()
                phrases.playingPrevious
            }
            phrases.isWhatsPlaying(heard) -> {
                val now = media.now.value
                if (now.isActive) listOf(now.title, now.artist).filter { it.isNotBlank() }.joinToString(phrases.by)
                else phrases.nothingPlaying
            }
            else -> null
        }
    }

    private fun navCommand(navigator: CarNavigator, heard: String): String? = when {
        phrases.isBack(heard) -> {
            navigator.back()
            phrases.goingBack
        }
        heard in phrases.exitWords -> {
            BuiltInApps.Exit.open(navigator)
            phrases.backToCar
        }
        phrases.isHome(heard) -> {
            navigator.home()
            phrases.headingHome
        }
        phrases.isApps(heard) -> {
            navigator.apps()
            phrases.hereAreApps
        }
        phrases.isSettings(heard) -> {
            navigator.settings()
            phrases.openingSettings
        }
        phrases.isVehicleData(heard) -> {
            if (CarSettings.experimentalFeaturesEnabled.value && CarSettings.carSensorsEnabled.value) {
                navigator.vehicleData()
                phrases.openingVehicleData
            } else {
                phrases.vehicleDataOff
            }
        }
        else -> null
    }

    private fun infoCommand(heard: String): String? = when {
        phrases.isTime(heard) -> phrases.timeIs(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()))
        phrases.isWeather(heard) -> {
            val data = (Weather.state.value as? WeatherState.Ready)?.data
            if (data != null) phrases.weatherIs("${data.temp}${data.tempUnit}", data.code, data.place)
            else phrases.noWeather
        }
        else -> null
    }

    /** "What's next": the first event on the dashboard's agenda, said with its day and time. */
    private suspend fun calendarCommand(context: Context, heard: String): String? {
        if (!phrases.isCalendar(heard)) return null
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return phrases.needsCalendarPermission
        }
        val event = withContext(Dispatchers.IO) { fetchAgenda(context) }.firstOrNull() ?: return phrases.calendarEmpty
        val start = Date(event.startMillis)
        val day = if (isToday(event)) phrases.today
        else SimpleDateFormat("EEEE", SpeechModels.language.value.locale).apply { timeZone = eventZone(event) }.format(start)
        val time = if (event.allDay) null else DateFormat.getTimeInstance(DateFormat.SHORT).format(start)
        return phrases.nextEvent(event.title, day, time)
    }

    private suspend fun callCommand(context: Context, heard: String): String? {
        val name = (
            after(heard, phrases.callTriggers)
                ?: phrases.giveACall.find(heard)?.groupValues?.get(1)
                ?: return null
            ).replace(phrases.whichPhone, "").trim()
        if (name.isBlank()) return phrases.whoToCall
        if (!hasCallPermissions(context)) return phrases.needsCallPermissions

        val match = withContext(Dispatchers.IO) {
            loadContacts(context).firstOrNull { fold(it.name).contains(name) }
        } ?: return phrases.notInContacts(name)

        placeCall(context, match.number)
        return phrases.calling(match.name)
    }

    private suspend fun openAppCommand(
        context: Context,
        navigator: CarNavigator,
        frame: CarEnvironment.Frame,
        heard: String,
    ): String? {
        val name = appName(after(heard, phrases.openTriggers) ?: return null)
        if (name.isBlank()) return null
        val wanted = phrases.appNicknames[name] ?: name

        // Gearslip's own apps come first in the list, so "phone" opens its dialer, not the phone's.
        val match = bestMatch(allEntries(context), wanted) { it.label }
            ?: return phrases.noAppCalled(name)

        launchEntry(match, navigator, frame)
        return phrases.opening(match.label)
    }

    private const val APP_CONNECT_MS = 5_000L
}
