package app.seb3thehacker.gearslip.car

import app.seb3thehacker.gearslip.speech.SpeechModels
import app.seb3thehacker.gearslip.speech.SpeechModels.Language

/**
 * What [AssistantCommands] listens for and says back, in one language. What it hears arrives
 * lowercase and without accents, so the words to listen for are written that way too; what it
 * says is written properly, for text-to-speech. A new language is one more of these, added to
 * [current].
 */
internal interface AssistantPhrases {

    // Heard
    /** Padding stripped from the front of a command, longest first where they overlap. */
    val leadFillers: List<String>
    /** Longest first, so "right now" goes whole rather than leaving "right". */
    val tailFillers: List<String>
    fun isHelp(t: String): Boolean
    /** Stripped from an app's name: "the", "my", "app". */
    val nameLeads: List<String>
    val nameTails: List<String>
    /** Other names people use for Gearslip's own apps. */
    val appNicknames: Map<String, String>

    /** "use X to navigate to Y": the app, then the rest. */
    val appFirst: Regex
    val navigateTriggers: List<String>
    /** The triggers that can mean an app or a screen as well as a place. */
    val goTo: List<String>
    /** Screens "go to X" means rather than a place. */
    val screenWords: Set<String>
    /** Before a nav app's name at the end: "X with Organic Maps". */
    val navAppSeparators: List<String>
    /** Longest first, so "the nearest " goes before "the ". */
    val placeLeads: List<String>
    /** The [placeLeads] that don't mean "near me". */
    val articles: Set<String>
    val placeTails: List<String>

    val playTriggers: List<String>
    /** Stripped from what to play: "some". */
    val playLeads: List<String>
    /** What "play X" leaves to the media commands: X is just the music, not a search. */
    val resumeWords: Set<String>
    /** Before a music app's name at the end: "X on Metrolist". */
    val musicAppSeparators: List<String>
    fun isPause(t: String): Boolean
    fun isResume(t: String): Boolean
    fun isNext(t: String): Boolean
    fun isPrevious(t: String): Boolean
    fun isWhatsPlaying(t: String): Boolean

    fun isBack(t: String): Boolean
    val exitWords: Set<String>
    fun isHome(t: String): Boolean
    fun isApps(t: String): Boolean
    fun isSettings(t: String): Boolean
    fun isVehicleData(t: String): Boolean

    fun isTime(t: String): Boolean
    fun isWeather(t: String): Boolean
    fun isCalendar(t: String): Boolean

    val callTriggers: List<String>
    /** "give X a call": the name. */
    val giveACall: Regex
    /** "on their mobile" and the like, after the name. */
    val whichPhone: Regex
    val openTriggers: List<String>

    // Said
    val helpShown: String
    val unknown: String
    val whereTo: String
    val noNavApps: String
    fun noNavAppCalled(name: String): String
    val notNavApp: String
    fun findingNearest(place: String, app: String): String
    fun navigating(place: String, app: String): String
    fun opening(app: String): String
    val openMusicFirst: String
    val musicAppTooSlow: String
    fun ignoresSearch(app: String): String
    fun lookingFor(query: String, app: String): String
    val paused: String
    val playing: String
    val skipping: String
    val playingPrevious: String
    /** Between the song and the artist. */
    val by: String
    val nothingPlaying: String
    val goingBack: String
    val backToCar: String
    val headingHome: String
    val hereAreApps: String
    val openingSettings: String
    val openingVehicleData: String
    val vehicleDataOff: String
    fun timeIs(time: String): String
    /** [code] is one of Open-Meteo's WMO weather codes. */
    fun weatherIs(temperature: String, code: Int, place: String): String
    val noWeather: String
    val needsCalendarPermission: String
    val calendarEmpty: String
    val today: String
    /** [time] is null for an all-day event. */
    fun nextEvent(title: String, day: String, time: String?): String
    val whoToCall: String
    val needsCallPermissions: String
    fun notInContacts(name: String): String
    fun calling(name: String): String
    fun noAppCalled(name: String): String

    /** Say this, and this happens: the list "help" shows. */
    val helpCommands: List<Pair<String, String>>

    companion object {
        val current: AssistantPhrases
            get() = when (SpeechModels.language.value) {
                Language.ENGLISH -> EnglishPhrases
                Language.SPANISH -> SpanishPhrases
            }
    }
}

internal object EnglishPhrases : AssistantPhrases {

    override val leadFillers = listOf(
        "hey gearslip", "ok gearslip", "okay gearslip", "gearslip", "hey", "ok", "okay", "um", "uh", "so",
        "please", "can you please", "can you", "could you", "would you", "will you", "can i", "could i",
        "i want you to", "i want to", "i'd like to", "i would like to", "i wanna", "i need to", "i need you to",
        "let's", "lets", "go ahead and", "just", "now", "quickly",
    )
    override val tailFillers = listOf("thank you very much", "thank you", "right now", "for me", "please", "thanks", "now")

    private val HELP_WORDS = setOf("help", "commands", "voice commands", "list commands", "show commands")
    override fun isHelp(t: String) = t in HELP_WORDS || t.startsWith("what can i say") || t.startsWith("what can you do")

    override val nameLeads = listOf("the ", "my ")
    override val nameTails = listOf(" app")
    override val appNicknames = mapOf(
        "web" to "browser", "internet" to "browser", "the web" to "browser", "web browser" to "browser",
        "dialer" to "phone", "the dialer" to "phone", "keypad" to "phone",
        "screen share" to "screen sharing", "screen mirroring" to "screen sharing", "mirroring" to "screen sharing",
    )

    override val appFirst = Regex("^(?:use|using|with|open|in|on) (.+?) (?:to |and )(.+)$")
    override val navigateTriggers = listOf(
        "navigate to ", "navigate me to ", "directions to ", "get directions to ", "give me directions to ",
        "take me to ", "drive to ", "drive me to ", "get me to ", "go to ", "head to ", "route to ",
        "how do i get to ", "show me the way to ", "find a route to ", "navigation to ", "get to ",
        // Last, so "navigate to" wins: the recognizer sometimes drops the "to".
        "navigate me ", "navigate ", "find me the nearest ", "find the nearest ", "find the closest ",
    )
    override val goTo = listOf("go to ")
    override val screenWords = setOf("settings", "home", "the home screen", "home screen", "apps", "my apps", "all apps")
    override val navAppSeparators = listOf(" with ", " using ", " on ", " via ", " in ", " through ")
    override val placeLeads = listOf(
        "the nearest ", "the closest ", "a nearby ", "the nearby ", "nearest ", "closest ", "nearby ",
        "a ", "an ", "some ",
    )
    override val articles = setOf("a", "an", "the", "some")
    override val placeTails = listOf(" near me", " nearby", " near here", " around here", " close by", " closest to me")

    override val playTriggers = listOf("play me ", "play ", "put on ", "listen to ", "i want to hear ", "shuffle ")
    override val playLeads = listOf("some ")
    override val resumeWords = setOf("music", "the music", "some music", "something", "it", "again", "that", "my music")
    override val musicAppSeparators = listOf(" on ", " in ", " from ", " with ", " using ")

    private val RESUME_PHRASES = setOf("keep playing", "continue", "keep going", "start the music", "music on", "turn the music back on")
    override fun isPause(t: String) = "pause" in t || t.startsWith("stop") || t == "quiet" || t == "shut up"
    override fun isResume(t: String) = "resume" in t || "unpause" in t || t in RESUME_PHRASES ||
        (t.startsWith("play") && "playing" !in t)
    override fun isNext(t: String) = "next" in t || "skip" in t || t.startsWith("change the song")
    override fun isPrevious(t: String) = "previous" in t || "last song" in t || "back song" in t || "go back a song" in t ||
        "play that again" in t || "replay" in t
    override fun isWhatsPlaying(t: String) = ("what" in t && "playing" in t) || "what song" in t || "who sings" in t ||
        "who's singing" in t || "who is singing" in t || "what's this song" in t || "name of this song" in t

    override fun isBack(t: String) = t == "go back" || t == "back"
    override val exitWords = setOf("exit", "exit gearslip", "close gearslip", "quit", "quit gearslip", "open exit")
    override fun isHome(t: String) = "go home" in t || t == "home" || t == "home screen" || "go to the home screen" in t
    override fun isApps(t: String) = "open apps" in t || t == "apps" || "app list" in t || "all apps" in t || "my apps" in t || t == "go to apps"
    override fun isSettings(t: String) = "settings" in t
    override fun isVehicleData(t: String) = "vehicle data" in t || "car data" in t

    override fun isTime(t: String) = "what time" in t || "what's the time" in t || t == "time" || "the time" in t
    override fun isWeather(t: String) = "weather" in t || "going to rain" in t || "gonna rain" in t || "how hot" in t ||
        "how cold" in t || "temperature outside" in t
    override fun isCalendar(t: String) = "calendar" in t || "next event" in t || "next meeting" in t ||
        "my schedule" in t || "any meetings" in t

    override val callTriggers = listOf("call ", "phone ", "dial ", "ring ", "make a call to ", "place a call to ", "start a call with ")
    override val giveACall = Regex("^give (.+) a (?:call|ring)$")
    override val whichPhone = Regex(" (?:on|at) (?:their |his |her )?(?:mobile|cell|phone|home|work)$")
    override val openTriggers = listOf("open ", "launch ", "start ", "go to ", "switch to ", "show me ", "bring up ", "pull up ")

    override val helpShown = "Here's what you can say."
    override val unknown = "I don't know how to do that yet."
    override val whereTo = "Where to?"
    override val noNavApps = "You don't have any navigation apps installed."
    override fun noNavAppCalled(name: String) = "I couldn't find a navigation app called $name."
    override val notNavApp = "That's not a navigation app."
    override fun findingNearest(place: String, app: String) = "Finding the nearest $place with $app."
    override fun navigating(place: String, app: String) = "Navigating to $place with $app."
    override fun opening(app: String) = "Opening $app."
    override val openMusicFirst = "Open a music app first, then ask again."
    override val musicAppTooSlow = "Your music app didn't open in time. Try again."
    override fun ignoresSearch(app: String) = "$app ignores voice searches. Pick something on its screen instead."
    override fun lookingFor(query: String, app: String) = "Looking for $query on $app."
    override val paused = "Paused."
    override val playing = "Playing."
    override val skipping = "Skipping."
    override val playingPrevious = "Playing the previous track."
    override val by = " by "
    override val nothingPlaying = "Nothing's playing."
    override val goingBack = "Going back."
    override val backToCar = "Back to the car's screen."
    override val headingHome = "Heading home."
    override val hereAreApps = "Here are your apps."
    override val openingSettings = "Opening settings."
    override val openingVehicleData = "Opening vehicle data."
    override val vehicleDataOff = "Vehicle data is off. Turn it on under experimental features in settings."
    override fun timeIs(time: String) = "It's $time."
    override fun weatherIs(temperature: String, code: Int, place: String) = "$temperature and ${Weather.describe(code)} in $place."
    override val noWeather = "I don't have the weather yet."
    override val needsCalendarPermission = "Gearslip needs the calendar permission first, from the phone screen."
    override val calendarEmpty = "Nothing on your calendar this week."
    override val today = "today"
    override fun nextEvent(title: String, day: String, time: String?) =
        if (time == null) "Next up: $title, $day." else "Next up: $title, $day at $time."
    override val whoToCall = "Who should I call?"
    override val needsCallPermissions = "Gearslip needs the phone and contacts permissions first, from the phone screen."
    override fun notInContacts(name: String) = "I couldn't find $name in your contacts."
    override fun calling(name: String) = "Calling $name."
    override fun noAppCalled(name: String) = "I couldn't find an app called $name."

    override val helpCommands = listOf(
        "Navigate to…" to "Directions there. Add \"with Organic Maps\" to pick the app",
        "Play…" to "A song, artist, or album in the open music app",
        "Pause · Resume · Next · Previous" to "Control the music",
        "What's playing" to "The song and artist",
        "Call…" to "A contact by name",
        "Open…" to "Any app, like Browser, Phone, or Screen sharing",
        "Exit" to "Back to the car's own screen",
        "Go home · Open apps · Settings" to "Move around Gearslip",
        "What time is it · Weather" to "The time, or the forecast",
        "What's on my calendar" to "Your next calendar event",
        "Help" to "This list",
    )
}
