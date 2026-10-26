package app.seb3thehacker.gearslip.car

/** [AssistantPhrases] in Spanish. "al" is "a el", so each "X a " trigger also comes as "X al ". */
internal object SpanishPhrases : AssistantPhrases {

    private fun withAl(verbs: List<String>) = verbs.flatMap { listOf("$it a ", "$it al ") }

    override val leadFillers = listOf(
        "oye gearslip", "hola gearslip", "ok gearslip", "vale gearslip", "gearslip", "oye", "hola", "ok", "vale",
        "eh", "em", "bueno", "pues", "venga", "por favor", "puedes", "podrias", "me puedes", "me podrias",
        "quiero que", "necesito que", "me gustaria", "necesito", "quiero", "ahora",
    )
    override val tailFillers = listOf("muchas gracias", "gracias", "ahora mismo", "por favor", "porfa", "ahora")

    override fun isHelp(t: String) = t == "ayuda" || t == "comandos" || t.startsWith("que puedo decir") || t.startsWith("que puedes hacer")

    override val nameLeads = listOf("la aplicacion de ", "la app de ", "la aplicacion ", "la app ", "el ", "la ", "los ", "las ", "mi ", "mis ")
    override val nameTails = listOf(" app")
    override val appNicknames = mapOf(
        "navegador" to "browser", "internet" to "browser", "web" to "browser",
        "telefono" to "phone", "marcador" to "phone", "teclado" to "phone",
        "compartir pantalla" to "screen sharing", "pantalla compartida" to "screen sharing", "duplicar pantalla" to "screen sharing",
    )

    override val appFirst = Regex("^(?:usa|usando|con|abre|en) (.+?) (?:para |y )(.+)$")
    override val navigateTriggers = withAl(
        listOf(
            "navega", "navegar", "llevame", "llevarme", "ir", "ve", "vete", "conduce", "conducir",
            "como llego", "como voy", "como ir", "ruta", "indicaciones", "dame indicaciones", "indicaciones para ir", "direcciones",
        ),
    ) + listOf(
        // Last, so "navega a" wins: the recognizer sometimes drops the "a".
        "navega ", "llevame ",
    )
    override val goTo = withAl(listOf("ve", "ir"))
    override val screenWords = setOf("ajustes", "configuracion", "inicio", "pantalla de inicio", "apps", "aplicaciones", "todas las aplicaciones")
    override val navAppSeparators = listOf(" con ", " usando ", " en ", " por ", " mediante ")
    override val placeLeads = listOf("el ", "la ", "los ", "las ", "un ", "una ", "unos ", "unas ")
    override val articles = setOf("el", "la", "los", "las", "un", "una", "unos", "unas")
    override val placeTails = listOf(
        " mas cercana", " mas cercano", " mas proxima", " mas proximo", " cerca de mi", " cerca de aqui", " por aqui", " cerca",
    )

    override val playTriggers = listOf("ponme ", "pon ", "poner ", "reproduce ", "reproducir ", "quiero escuchar ", "escuchar ")
    override val playLeads = listOf("algo de ", "un poco de ", "musica de ", "canciones de ")
    override val resumeWords = setOf(
        "musica", "la musica", "algo", "algo de musica", "eso", "otra vez", "mi musica",
        // "pon en pausa", "pon la siguiente": controls, not searches.
        "pausa", "en pausa", "la siguiente", "la siguiente cancion", "la anterior", "la cancion anterior",
    )
    override val musicAppSeparators = listOf(" en ", " con ", " desde ", " usando ")

    private val RESUME_PHRASES = setOf("sigue", "sigue sonando", "quita la pausa", "dale al play", "play")
    override fun isPause(t: String) = "pausa" in t || t == "para" || t.startsWith("para la") || t.startsWith("parar") ||
        t.startsWith("deten") || t == "silencio" || t == "callate"
    override fun isResume(t: String) = "reanuda" in t || "continua" in t || t in RESUME_PHRASES ||
        t.startsWith("pon") || t.startsWith("reproduce")
    override fun isNext(t: String) = "siguiente" in t || "salta" in t || "cambia de cancion" in t || "otra cancion" in t
    override fun isPrevious(t: String) = "anterior" in t || "repite" in t || "la de antes" in t
    override fun isWhatsPlaying(t: String) = ("que" in t && "suena" in t) || "que cancion" in t || "quien canta" in t ||
        "como se llama esta cancion" in t

    override fun isBack(t: String) = t == "atras" || t == "vuelve" || t == "volver" || t == "vuelve atras"
    override val exitWords = setOf("salir", "sal", "salir de gearslip", "cierra gearslip", "cerrar gearslip", "abre salir")
    override fun isHome(t: String) = "inicio" in t
    override fun isApps(t: String) = "aplicaciones" in t || t == "apps" || "mis apps" in t || "lista de apps" in t
    override fun isSettings(t: String) = "ajustes" in t || "configuracion" in t
    override fun isVehicleData(t: String) = "datos del vehiculo" in t || "datos del coche" in t

    override fun isTime(t: String) = "que hora" in t || t == "hora" || "la hora" in t
    override fun isWeather(t: String) = "que tiempo" in t || "el tiempo" in t || t == "tiempo" || "clima" in t || "va a llover" in t || "llueve" in t ||
        "temperatura" in t || "hace frio" in t || "hace calor" in t
    override fun isCalendar(t: String) = "calendario" in t || "agenda" in t || "que tengo" in t || "proximo evento" in t ||
        "siguiente evento" in t || "proxima cita" in t || "proxima reunion" in t

    override val callTriggers = withAl(listOf("llama", "llamar", "marca", "telefonea", "haz una llamada", "hacer una llamada")) +
        // Last, so "llama a" wins: the recognizer sometimes drops the "a".
        listOf("llama ", "llamar ")
    override val giveACall = Regex("^dale un toque (?:a |al )?(.+)$")
    override val whichPhone = Regex(" (?:al|en el|a su) (?:movil|celular|telefono|fijo|casa|trabajo)$")
    override val openTriggers = listOf("abre ", "abrir ", "inicia ", "lanza ", "ve a ", "ir a ", "cambia a ", "muestrame ", "ensename ")

    override val helpShown = "Esto es lo que puedes decir."
    override val unknown = "Todavía no sé hacer eso."
    override val whereTo = "¿Adónde vamos?"
    override val noNavApps = "No tienes ninguna app de navegación instalada."
    override fun noNavAppCalled(name: String) = "No encuentro ninguna app de navegación llamada $name."
    override val notNavApp = "Esa no es una app de navegación."
    override fun findingNearest(place: String, app: String) = "Buscando $place cerca de ti con $app."
    override fun navigating(place: String, app: String) = "Navegando a $place con $app."
    override fun opening(app: String) = "Abriendo $app."
    override val openMusicFirst = "Abre primero una app de música y vuelve a pedírmelo."
    override val musicAppTooSlow = "Tu app de música no se abrió a tiempo. Inténtalo de nuevo."
    override fun ignoresSearch(app: String) = "$app no admite búsquedas por voz. Elige algo en su pantalla."
    override fun lookingFor(query: String, app: String) = "Buscando $query en $app."
    override val paused = "En pausa."
    override val playing = "Reproduciendo."
    override val skipping = "Siguiente."
    override val playingPrevious = "Pista anterior."
    override val by = " de "
    override val nothingPlaying = "No suena nada."
    override val goingBack = "Volviendo."
    override val backToCar = "De vuelta a la pantalla del coche."
    override val headingHome = "Al inicio."
    override val hereAreApps = "Aquí tienes tus apps."
    override val openingSettings = "Abriendo los ajustes."
    override val openingVehicleData = "Abriendo los datos del vehículo."
    override val vehicleDataOff = "Los datos del vehículo están desactivados. Actívalos en las funciones experimentales de los ajustes."
    override fun timeIs(time: String) = "Son las $time."
    override fun weatherIs(temperature: String, code: Int, place: String) = "$temperature y ${describe(code)} en $place."
    override val noWeather = "Todavía no tengo el tiempo."
    override val needsCalendarPermission = "Gearslip necesita primero el permiso del calendario, desde la pantalla del teléfono."
    override val calendarEmpty = "No tienes nada en el calendario esta semana."
    override val today = "hoy"
    override fun nextEvent(title: String, day: String, time: String?) =
        if (time == null) "Lo próximo: $title, $day." else "Lo próximo: $title, $day a las $time."
    override val whoToCall = "¿A quién llamo?"
    override val needsCallPermissions = "Gearslip necesita primero los permisos de teléfono y contactos, desde la pantalla del teléfono."
    override fun notInContacts(name: String) = "No encuentro a $name en tus contactos."
    override fun calling(name: String) = "Llamando a $name."
    override fun noAppCalled(name: String) = "No encuentro ninguna app llamada $name."

    /** [Weather.describe], in Spanish. */
    private fun describe(code: Int): String = when (code) {
        0 -> "despejado"
        1 -> "casi despejado"
        2 -> "parcialmente nublado"
        3 -> "cubierto"
        45, 48 -> "niebla"
        in 51..57 -> "llovizna"
        in 61..67 -> "lluvia"
        in 71..77 -> "nieve"
        in 80..82 -> "chubascos"
        in 85..86 -> "chubascos de nieve"
        in 95..99 -> "tormenta"
        else -> "-"
    }

    override val helpCommands = listOf(
        "Llévame a…" to "Indicaciones hasta allí. Añade \"con Organic Maps\" para elegir la app",
        "Pon…" to "Una canción, artista o álbum en la app de música abierta",
        "Pausa · Sigue · Siguiente · Anterior" to "Controla la música",
        "¿Qué suena?" to "La canción y el artista",
        "Llama a…" to "Un contacto por su nombre",
        "Abre…" to "Cualquier app, como Browser, Phone o Screen sharing",
        "Salir" to "Vuelve a la pantalla del coche",
        "Inicio · Aplicaciones · Ajustes" to "Muévete por Gearslip",
        "¿Qué hora es? · El tiempo" to "La hora o la previsión",
        "¿Qué tengo hoy?" to "Tu próximo evento del calendario",
        "Ayuda" to "Esta lista",
    )
}
