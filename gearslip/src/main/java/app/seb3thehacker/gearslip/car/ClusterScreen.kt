package app.seb3thehacker.gearslip.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.host.clockTime
import app.seb3thehacker.gearslip.host.display
import app.seb3thehacker.gearslip.host.remainingTime
import kotlinx.coroutines.delay

/**
 * The instrument-cluster screen: the next turn, big enough to read in a glance past the wheel.
 * The arrow and the distance to it, what to do there, and the time, distance and arrival left
 * along the bottom. With no route it shows what's playing, quietly, or nothing.
 *
 * Always dark, like the dials around it. [marginX] and [marginY] are the pixels the car crops off
 * the frame, kept clear.
 */
@Composable
internal fun ClusterScreen(marginX: Int, marginY: Int) {
    // The map app's turn changes a few times a minute: read it once a second.
    val turn by produceState<ClusterTurn?>(null) {
        while (true) {
            value = ClusterTurn.now()
            delay(1_000)
        }
    }
    val density = LocalDensity.current
    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(Color.Black).padding(
                    horizontal = with(density) { (marginX / 2).toDp() } + 24.dp,
                    vertical = with(density) { (marginY / 2).toDp() } + 16.dp,
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Sized off the height: clusters run from short strips to near squares.
                val unit = maxHeight / 12
                val t = turn
                if (t == null) {
                    NowPlayingLine(Modifier.align(Alignment.Center))
                    return@BoxWithConstraints
                }
                Column(verticalArrangement = Arrangement.spacedBy(unit * 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarGlyph(t.icon, Modifier.size(unit * 3.4f))
                        Spacer(Modifier.width(unit))
                        Text(
                            t.distance,
                            fontSize = with(density) { (unit * 2.2f).toSp() },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    if (t.what.isNotEmpty()) {
                        Text(
                            t.what,
                            fontSize = with(density) { (unit * 1.1f).toSp() },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (t.trip.isNotEmpty()) {
                        Text(
                            t.trip,
                            fontSize = with(density) { (unit * 0.85f).toSp() },
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingLine(modifier: Modifier) {
    val now = CarServices.mediaOrNull()?.now?.value
    if (now == null || now.title.isEmpty()) return
    Text(
        listOf(now.title, now.artist).filter { it.isNotEmpty() }.joinToString(" · "),
        modifier,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** What the cluster shows of a route, read from [NavTurn] and the trip's estimate to the destination. */
private class ClusterTurn(
    val icon: androidx.car.app.model.CarIcon?,
    val distance: String,
    val what: String,
    val trip: String,
) {
    companion object {
        fun now(): ClusterTurn? {
            val turn = NavTurn.current() ?: return null
            if (turn.rerouting) return null
            val step = turn.step
            val cue = step.cue?.toString().orEmpty()
            val what = cue.ifEmpty { step.road?.toString().orEmpty() }
            val estimate = CarServices.navOrNull()?.trip?.value?.destinationTravelEstimates?.firstOrNull()
            val trip = estimate?.let { e ->
                listOf(
                    e.remainingTimeSeconds.takeIf { it >= 0 }?.let { remainingTime(it) }.orEmpty(),
                    e.remainingDistance.display(),
                    e.arrivalTimeAtDestination.clockTime(),
                ).filter { it.isNotEmpty() }.joinToString(" · ")
            }.orEmpty()
            return ClusterTurn(step.maneuver?.icon, turn.distance.display(), what, trip)
        }
    }
}
