package app.seb3thehacker.gearslip.car

import app.seb3thehacker.gearslip.car.theme.*
import android.graphics.Rect
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.MapTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import app.seb3thehacker.gearslip.host.clockTime
import app.seb3thehacker.gearslip.host.color
import app.seb3thehacker.gearslip.host.image
import app.seb3thehacker.gearslip.host.display
import app.seb3thehacker.gearslip.host.remainingTime
import app.seb3thehacker.gearslip.host.text

/**
 * Draws the template an app handed us, over the surface it is drawing its map into.
 *
 * A templated app never draws its own chrome: it describes a screen in the Car App Library's
 * model types and every host renders those to suit its own screen. This is Gearslip's
 * rendering. It is deliberately laid out for a wide, short car display - controls to the
 * right where a hand reaches from the driver's seat, information to the left.
 *
 * Templates split into two kinds. The six map-backed ones here leave room for the app's own
 * drawing and lay chrome around it; everything else fills the screen and is drawn by
 * [ContentTemplate].
 */
@Composable
fun TemplateChrome(template: Template?, modifier: Modifier = Modifier) {
    when (template) {
        null -> Unit
        is NavigationTemplate -> NavigationChrome(template, modifier)
        is MapWithContentTemplate -> MapWithContentChrome(template, modifier)
        is MapTemplate -> MapChrome(template, modifier)
        is PlaceListNavigationTemplate -> PlaceListNavigationChrome(template, modifier)
        is RoutePreviewNavigationTemplate -> RoutePreviewChrome(template, modifier)
        is PlaceListMapTemplate -> PlaceListMapChrome(template, modifier)
        else -> ContentTemplate(template, modifier)
    }
}

/**
 * How much of the surface Gearslip's chrome covers, so the app can keep what matters clear of
 * it. Expressed as the visible rectangle within a [width] x [height] surface.
 *
 * Navigation's cards are measured where they land ([measured], from [ChromeCover]); an app
 * centres its position marker in this rectangle, so a guess puts the marker under the card.
 * A template that isn't map-backed covers the surface completely, and reporting an empty
 * rectangle is how the app is told to stop bothering.
 */
fun visibleAreaFor(template: Template?, width: Int, height: Int, measured: Rect? = null): Rect = when (template) {
    is NavigationTemplate -> measured ?: Rect(0, 0, width, height)
    is MapWithContentTemplate, is MapTemplate, is PlaceListNavigationTemplate,
    is RoutePreviewNavigationTemplate, is PlaceListMapTemplate,
    -> Rect((width * CONTENT_PANE_FRACTION).toInt(), 0, width, measured?.bottom ?: height)
    null -> Rect(0, 0, width, height)
    else -> Rect(0, 0, 0, 0)
}

/** The side of the map a piece of chrome sits against, and so the side it shrinks the visible area from. */
internal enum class CoverEdge { TOP, BOTTOM, END }

/**
 * Where the chrome over the map actually landed, in window pixels. The map buttons count even
 * while faded out, or whatever an app draws in a corner (Waze's speedometer) is under them the
 * moment they come back.
 */
internal class ChromeCover {
    private val pieces = mutableStateMapOf<String, Pair<CoverEdge, ComposeRect>>()

    fun put(key: String, edge: CoverEdge, bounds: ComposeRect) {
        val piece = edge to bounds
        if (pieces[key] != piece) pieces[key] = piece
    }

    fun remove(key: String) {
        pieces.remove(key)
    }

    /** What is left of a [width] x [height] surface at [surface] (window pixels), in surface pixels. */
    fun visibleWithin(surface: ComposeRect, width: Int, height: Int): Rect {
        var left = 0f
        var top = 0f
        var right = width.toFloat()
        var bottom = height.toFloat()
        pieces.values.forEach { (edge, b) ->
            when (edge) {
                CoverEdge.TOP -> top = maxOf(top, b.bottom - surface.top)
                CoverEdge.BOTTOM -> bottom = minOf(bottom, b.top - surface.top)
                CoverEdge.END -> right = minOf(right, b.left - surface.left)
            }
        }
        // Never hand an app nothing to draw in, however the cards end up.
        if (right - left < width / 4f) { left = 0f; right = width.toFloat() }
        if (bottom - top < height / 4f) { top = 0f; bottom = height.toFloat() }
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }
}

internal val LocalChromeCover = compositionLocalOf<ChromeCover?> { null }

/**
 * The floating nav bar, as far as the app's map is concerned. Its full [height] at once rather
 * than following the slide, so the app is told once instead of every frame.
 */
@Composable
internal fun BottomCover(height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).covers("nav-bar", CoverEdge.BOTTOM))
}

/** Records where this lands, padding included, until it leaves the screen. */
@Composable
internal fun Modifier.covers(key: String, edge: CoverEdge): Modifier {
    val cover = LocalChromeCover.current ?: return this
    DisposableEffect(cover, key) { onDispose { cover.remove(key) } }
    return onGloballyPositioned { cover.put(key, edge, it.boundsInWindow()) }
}

// --- navigation ------------------------------------------------------------------------------

@Composable
private fun NavigationChrome(template: NavigationTemplate, modifier: Modifier) {
    val pan by CarServices.nav.panMode.collectAsState()
    // Pan mode clears the map for looking around: only the buttons stay.
    val routing = (template.navigationInfo as? RoutingInfo)?.takeUnless { pan }
    val estimate = template.destinationTravelEstimate?.takeUnless { pan }
    NavigationBars(template, routing, estimate, modifier)
}

/**
 * The turn in one short bar across the top, the arrival time in another along the bottom, so the
 * map keeps its full width. Neither grows past [NAV_BAR_MAX_WIDTH]. The bottom one is short and
 * left out of the visible area on purpose, so a speedometer in the app's bottom corner sits level
 * with it rather than stacked above it. The map buttons sit between them on the right.
 */
@Composable
private fun NavigationBars(
    template: NavigationTemplate,
    routing: RoutingInfo?,
    estimate: TravelEstimate?,
    modifier: Modifier,
) {
    val pan by CarServices.nav.panMode.collectAsState()
    Column(modifier.fillMaxSize()) {
        routing?.let {
            RoutingBar(
                it,
                Modifier.covers("nav-top", CoverEdge.TOP).padding(start = 12.dp, top = 12.dp, end = 12.dp)
                    .widthIn(max = NAV_BAR_MAX_WIDTH).fillMaxWidth(),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                estimate?.let {
                    TravelEstimateBar(
                        it,
                        Modifier.align(Alignment.BottomStart).padding(12.dp)
                            .widthIn(max = NAV_BAR_MAX_WIDTH),
                    )
                }
            }
            RightEdgeControls(
                if (pan) null else template.actionStrip,
                template.mapActionStrip,
                Modifier.padding(12.dp),
                // Only the column: the app's own buttons are a wide row up top, over the map's
                // edge rather than where a speedometer or the like would go.
                mapActionsModifier = Modifier.covers("nav-buttons", CoverEdge.END),
            )
        }
    }
}

/** The manoeuvre in one row: arrow, distance, what to do, onto what road, and lanes or the next turn. */
@Composable
private fun RoutingBar(info: RoutingInfo, modifier: Modifier = Modifier) {
    if (info.isLoading) {
        ChromeSurface(modifier) {
            Text("Finding a route…", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = ChromeType.title)
        }
        return
    }

    val step = info.currentStep ?: return
    ChromeSurface(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarGlyph(step.maneuver?.icon, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                val distance = info.currentDistance.display()
                if (distance.isNotEmpty()) {
                    Text(distance, style = ChromeType.headline, maxLines = 1)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(step.cue.text(), style = ChromeType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val road = step.road.text()
                    if (road.isNotEmpty()) {
                        Text(
                            road,
                            style = ChromeType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val next = info.nextStep
                if (step.lanesImage != null || step.lanes.orEmpty().isNotEmpty()) {
                    LaneGuidance(step, Modifier.padding(start = 12.dp).widthIn(max = 200.dp).heightIn(max = 48.dp))
                } else if (next != null) {
                    Spacer(Modifier.width(12.dp))
                    Text("then", style = ChromeType.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    CarGlyph(next.maneuver?.icon, Modifier.size(24.dp))
                }
            }
            // Rare, and only where an arrow can't explain the junction: worth the bar growing for.
            info.junctionImage?.let { junction ->
                Spacer(Modifier.height(8.dp))
                JunctionImage(junction)
            }
        }
    }
}

/** The zoomed-in intersection an app draws for a junction too complex for an arrow to explain. */
@Composable
private fun JunctionImage(icon: CarIcon, modifier: Modifier = Modifier) {
    val bitmap = icon.image(LocalContext.current) ?: return
    Image(
        bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().heightIn(max = 120.dp),
    )
}

/**
 * Which lane to be in before the manoeuvre, either as the app's own picture ([Step.getLanesImage])
 * or as a row of arrows built from its structured lane data - whichever it sent. The recommended
 * lane, or lanes, stand out in the theme's own colour; the rest sit on the same muted, solid tile
 * everything else uses, never dimmed by transparency.
 */
@Composable
private fun LaneGuidance(step: Step, modifier: Modifier = Modifier) {
    step.lanesImage?.let {
        JunctionImage(it, modifier)
        return
    }
    val lanes = step.lanes.orEmpty()
    if (lanes.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        lanes.forEach { lane -> LaneTile(lane) }
    }
}

@Composable
private fun LaneTile(lane: Lane) {
    val directions = lane.directions.orEmpty()
    val shown = directions.firstOrNull { it.isRecommended } ?: directions.firstOrNull()
    val recommended = shown?.isRecommended == true
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (recommended) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = if (recommended) "Recommended lane" else "Lane",
            tint = if (recommended) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).rotate(shown.turnDegrees()),
        )
    }
}

/** The one arrow glyph this app draws, turned to stand in for every shape a lane can permit. */
private fun LaneDirection?.turnDegrees(): Float = when (this?.shape) {
    LaneDirection.SHAPE_SLIGHT_RIGHT -> 30f
    LaneDirection.SHAPE_NORMAL_RIGHT -> 90f
    LaneDirection.SHAPE_SHARP_RIGHT -> 135f
    LaneDirection.SHAPE_U_TURN_RIGHT -> 170f
    LaneDirection.SHAPE_SLIGHT_LEFT -> -30f
    LaneDirection.SHAPE_NORMAL_LEFT -> -90f
    LaneDirection.SHAPE_SHARP_LEFT -> -135f
    LaneDirection.SHAPE_U_TURN_LEFT -> -170f
    else -> 0f
}

/**
 * The trip at a glance: time and distance left, the clock time it ends at, and - beside them,
 * where a thumb reaches - the app's own way to stop navigating. One short line along the bottom,
 * stopping short of the map buttons. The close button is Gearslip's own, not the app's: an
 * actionStrip's actions carry no flag saying which one (if any) means "stop navigating" - the
 * first button here turned out to be a mute toggle, not an exit - so this leaves the app the way
 * going Home already does, rather than guess at the app's own controls and risk getting it wrong.
 */
@Composable
private fun TravelEstimateBar(estimate: TravelEstimate, modifier: Modifier = Modifier) {
    val time = remainingTime(estimate.remainingTimeSeconds)
    val distance = estimate.remainingDistance.display()
    val arrival = estimate.arrivalTimeAtDestination.clockTime()
    val trip = estimate.tripText.text()
    if (time.isEmpty() && distance.isEmpty() && arrival.isEmpty() && trip.isEmpty()) return

    val detail = listOfNotNull(
        arrival.takeIf { it.isNotEmpty() },
        distance.takeIf { it.isNotEmpty() },
        trip.takeIf { it.isNotEmpty() },
    ).joinToString("  •  ")

    // The same solid card the manoeuvre bar above it uses - one surface for the whole map
    // chrome, rather than this one spot styled on its own as a bar over the map.
    ChromeSurface(modifier, shadowElevation = 8.dp) {
        val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()
        Row(
            Modifier.padding(start = 8.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Not a disconnect: the connection stays up and the app decides its own next screen.
            // Ending navigation is its own call, separate from a back press - a back press only
            // pops the on-screen card, leaving the app still believing it is navigating (so a
            // freshly picked destination silently does nothing); this is the call that actually
            // clears that flag on the app's side.
            GsIconButton(
                Icons.Filled.Close,
                "Stop navigating",
                { CarServices.nav.stopNavigating() },
                colors = GsColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface),
                size = 40.dp,
                iconSize = 20.dp,
                shape = CircleShape,
            )
            Spacer(Modifier.width(10.dp))
            if (time.isNotEmpty()) {
                Text(
                    time,
                    style = ChromeType.headline,
                    color = estimate.remainingTimeColor.color(dark, MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                )
            }
            if (detail.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Text(
                    detail,
                    style = ChromeType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// --- map with a side pane ----------------------------------------------------------------------

/**
 * The shape every remaining map-backed template shares: the app's map across the whole surface,
 * the template's content in a card floating over its left side, and both action strips stacked at
 * the right edge. The card is as tall as its content, up to the height of the screen, so a
 * handful of buttons no longer claim a whole column of the map. [fullHeight] is for content that
 * needs the room, such as a search field with its keyboard.
 */
@Composable
private fun MapPaneLayout(
    modifier: Modifier,
    actionStrip: androidx.car.app.model.ActionStrip?,
    mapActionStrip: androidx.car.app.model.ActionStrip?,
    fullHeight: Boolean = false,
    pane: @Composable () -> Unit,
) {
    // Pan mode hands the whole screen to the map, leaving only its own controls.
    val pan by CarServices.nav.panMode.collectAsState()
    val edgeActions = (actionStrip?.actions.orEmpty() + mapActionStrip?.actions.orEmpty()).map { it.identity() }.toSet()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        if (!pan) {
            ChromeSurface(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .width(maxOf(maxWidth * CONTENT_PANE_FRACTION - 12.dp, 232.dp))
                    .let { if (fullHeight) it.height(maxHeight - 24.dp) else it.heightIn(max = maxHeight - 24.dp) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalMapEdgeActions provides edgeActions) { pane() }
            }
        }
        RightEdgeControls(
            if (pan) null else actionStrip,
            mapActionStrip,
            Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
    }
}

@Composable
private fun MapWithContentChrome(template: MapWithContentTemplate, modifier: Modifier) {
    MapPaneLayout(
        modifier,
        template.actionStrip,
        template.mapController?.mapActionStrip,
        fullHeight = template.contentTemplate.needsFullHeight(),
    ) {
        // A pane floating over the map is a place or a route the app is proposing, so its one
        // action - Start - counts itself down rather than waiting to be pressed; the same pane
        // filling a whole screen elsewhere gets no such push.
        ContentTemplate(template.contentTemplate, Modifier, autoStartPane = true)
    }
}

@Composable
private fun MapChrome(template: MapTemplate, modifier: Modifier) {
    MapPaneLayout(
        modifier,
        template.actionStrip,
        template.mapController?.mapActionStrip,
    ) {
        ChromeSurface(Modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)) {
            Column {
                HeaderBar(
                    headerTitle(template.header, ""),
                    template.header?.startHeaderAction,
                    template.header?.endHeaderActions.orEmpty(),
                )
                if (template.pane != null) PaneRows(template.pane, autoStart = true)
                else ItemListColumn(template.itemList)
            }
        }
    }
}

@Composable
private fun PlaceListNavigationChrome(template: PlaceListNavigationTemplate, modifier: Modifier) {
    MapPaneLayout(modifier, template.actionStrip, template.mapActionStrip) {
        ListPane(
            headerTitle(template.header, template.title.text()),
            template.header?.startHeaderAction ?: template.headerAction,
            template.isLoading,
            template.itemList,
            Modifier,
        )
    }
}

@Composable
private fun PlaceListMapChrome(template: PlaceListMapTemplate, modifier: Modifier) {
    MapPaneLayout(modifier, template.actionStrip, null) {
        ListPane(
            template.title.text(),
            template.headerAction,
            template.isLoading,
            template.itemList,
            Modifier,
        )
    }
}

/** Route choices down the side, with the app's "start" action pinned under them. */
@Composable
private fun RoutePreviewChrome(template: RoutePreviewNavigationTemplate, modifier: Modifier) {
    MapPaneLayout(modifier, template.actionStrip, template.mapActionStrip) {
        ChromeSurface(Modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)) {
            Column {
                HeaderBar(
                    headerTitle(template.header, template.title.text()),
                    template.header?.startHeaderAction ?: template.headerAction,
                    template.header?.endHeaderActions.orEmpty(),
                )
                Box(Modifier.weight(1f, fill = false)) {
                    if (template.isLoading) {
                        Text("Finding routes…", Modifier.padding(14.dp), style = ChromeType.title)
                    } else {
                        ItemListColumn(template.itemList)
                    }
                }
                template.navigateAction?.let {
                    AutoStartButton(it, Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ListPane(
    title: String,
    startAction: androidx.car.app.model.Action?,
    loading: Boolean,
    itemList: androidx.car.app.model.ItemList?,
    modifier: Modifier,
) {
    ChromeSurface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)) {
        Column {
            HeaderBar(title, startAction)
            if (loading) Text("Loading…", Modifier.padding(14.dp), style = ChromeType.title)
            else ItemListColumn(itemList)
        }
    }
}

/** Fraction of the surface the content pane covers beside a map. */
private const val CONTENT_PANE_FRACTION = 0.42f


/** Past this the bars only spread the same words further apart, across a map left with no music beside it. */
private val NAV_BAR_MAX_WIDTH = 460.dp

/** Search, sign-in and the like are laid out for a whole column, so they keep one. */
private fun Template?.needsFullHeight() =
    this is androidx.car.app.model.SearchTemplate || this is androidx.car.app.model.signin.SignInTemplate ||
        this is androidx.car.app.model.TabTemplate || this is androidx.car.app.model.LongMessageTemplate
