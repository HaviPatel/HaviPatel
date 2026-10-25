package com.reatv.launcher.ui.home

import android.graphics.Bitmap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.ShelfArt
import com.reatv.launcher.data.shelf.Recommendations
import com.reatv.launcher.data.shelf.ShelfItem
import com.reatv.launcher.glass.Blur
import com.reatv.launcher.glass.glassEdge
import com.reatv.launcher.ui.LocalArt
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.LocalShelfContent
import com.reatv.launcher.ui.LocalUiSounds
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.tvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * tvOS Top Shelf, full-bleed above the app rows. When the focused top-row app has series and movies
 * (its own recommendations, or TMDB's catalog for that service) they play as a carousel: full-screen
 * artwork, the title's logo, a line of details and a short description, moving on by itself. Press
 * Up to step into it, Left/Right to browse, OK to open, Down or Back to return. Apps without titles
 * show their brand instead: the logo lifted off their banner over their colors.
 */
@Composable
fun TopShelf(
    app: AppEntry?,
    metrics: HomeMetrics,
    state: HomeState,
    interactive: Boolean,
    collapse: () -> Float,
    onOpen: (AppEntry, ShelfItem) -> Unit,
) {
    val shelfHeight = metrics.shelfHeight
    Box(
        Modifier
            .fillMaxWidth()
            .height(shelfHeight)
            .clipToBounds()
            .graphicsLayer {
                val c = collapse()
                alpha = 1f - c
                translationY = -60.dp.toPx() * c
            },
    ) {
        if (app != null) ShelfFor(app, metrics, state, interactive, onOpen)
    }
}

private sealed interface Shelf {
    val app: AppEntry
}

private class Brand(override val app: AppEntry, val art: ShelfArt) : Shelf

private class Titles(override val app: AppEntry, val items: List<ShelfItem>) : Shelf

/** What the crossfade keys on: a new list for the same app and kind updates in place. */
private data class ShelfKey(val app: String, val titles: Boolean)

private fun Shelf.key() = ShelfKey(app.key, this is Titles)

@Composable
private fun ShelfFor(
    app: AppEntry,
    metrics: HomeMetrics,
    state: HomeState,
    interactive: Boolean,
    onOpen: (AppEntry, ShelfItem) -> Unit,
) {
    val artLoader = LocalArt.current
    val content = LocalShelfContent.current
    val settings = LocalSettings.current
    val density = LocalDensity.current
    // The brand fallback's card, around the banner's native size: big enough to read, never a billboard.
    val cardHeight = min(metrics.firstRowTop * 0.52f, 180.dp)
    val card = DpSize(cardHeight * (16f / 9f), cardHeight)
    val cardPx = with(density) { card.width.roundToPx() to card.height.roundToPx() }

    val recommended by Recommendations.items.collectAsState()
    val recs = if (settings.shelfRecommendations) recommended[app.packageName].orEmpty() else emptyList()
    val catalogOn = content.hasCatalog(app)

    // Whatever can be shown without waiting: the app's recommendations, a fetched catalog, its brand.
    fun ready(): Shelf? {
        if (recs.isNotEmpty()) return Titles(app, recs)
        if (catalogOn) content.peekCatalog(app)?.let { return Titles(app, it) }
        return artLoader.peekShelf(app.key)?.let { Brand(app, it) }
    }

    // The shelf being left stays up until the next one is ready, then crossfades: no empty frame.
    val model by produceState(ready(), app.key, recs, catalogOn) {
        val now = ready()
        if (now != null) value = now
        if (now is Titles) return@produceState
        if (now == null) {
            // Gliding along the top row shouldn't decode and key every banner it passes.
            delay(140)
            value = Brand(app, artLoader.shelf(app, cardPx.first, cardPx.second))
        }
        if (catalogOn) {
            delay(250)
            val items = content.catalog(app)
            if (items.isNotEmpty()) value = Titles(app, items)
        }
    }

    // The titles went away under focus (a recommendation was withdrawn): back to the app's tile.
    val showingTitles = model is Titles
    LaunchedEffect(showingTitles) {
        if (!showingTitles && state.shelfFocused) {
            withFrameNanos { }
            state.shelfFocused = false
            state.requestFocus(app.key)
        }
    }

    val currentKey = model?.key()
    Crossfade(targetState = currentKey, animationSpec = tween(420), label = "shelf") { k ->
        if (k == null) return@Crossfade
        // The incoming shelf follows the live model; the outgoing one keeps what it had.
        val born = remember(k) { model }
        val m = (if (k == currentKey) model else born) ?: return@Crossfade
        when (m) {
            is Brand -> BrandShelf(m.app, m.art, metrics, card, state, onScreen = k == currentKey)
            is Titles -> TitlesCarousel(m.app, m.items, metrics, state, onScreen = k == currentKey, interactive = interactive, onOpen)
        }
    }
}

// --- Titles ----------------------------------------------------------------------------------

private const val ADVANCE_MS = 8_000L

@Composable
private fun TitlesCarousel(
    app: AppEntry,
    items: List<ShelfItem>,
    metrics: HomeMetrics,
    state: HomeState,
    onScreen: Boolean,
    interactive: Boolean,
    onOpen: (AppEntry, ShelfItem) -> Unit,
) {
    val content = LocalShelfContent.current
    val sounds = LocalUiSounds.current
    val reduceMotion = LocalSettings.current.reduceMotion
    var index by remember(app.key) { mutableIntStateOf(0) }
    val current = index.coerceIn(0, items.lastIndex)
    val item = items[current]
    val count = items.size
    // Only the shelf on its way in takes focus, never the one fading out, and not while editing.
    val takesFocus = onScreen && interactive && !state.editing

    // The dock's glass refracts whatever the shelf shows.
    val shown by produceState(content.peekBackdrop(item), item.key) {
        if (value == null) value = content.backdrop(item)
    }
    LaunchedEffect(onScreen, shown, item.key) {
        if (onScreen) state.shelfArt = ShelfGlass(shown?.asAndroidBitmap() ?: item.card, android.graphics.Color.BLACK)
    }

    // Moves on by itself like tvOS, but only while you're watching it: not while you browse it,
    // not behind an overlay, not while another app is playing. The next backdrop is loaded first,
    // so a slide never fades in empty.
    LaunchedEffect(app.key, items, current, state.shelfFocused, takesFocus, state.visible) {
        if (count < 2 || state.shelfFocused || !takesFocus || !state.visible) return@LaunchedEffect
        delay(ADVANCE_MS)
        val next = (current + 1) % count
        content.backdrop(items[next])
        index = next
    }
    // Neighbours ready for Left/Right.
    LaunchedEffect(items, current, state.visible) {
        if (!state.visible || count < 2) return@LaunchedEffect
        for (i in listOf(current + 1, current - 1)) {
            val neighbour = items[(i + count) % count]
            content.backdrop(neighbour)
            content.logo(neighbour)
        }
    }

    val focus by animateFloatAsState(if (state.shelfFocused) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 400f), label = "shelfFocus")
    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged {
                state.shelfFocused = it.isFocused
                if (it.isFocused) sounds?.onFocusMoved()
            }
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != AndroidKeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (native.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        index = (current - 1 + count) % count
                        sounds?.onFocusMoved()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        index = (current + 1) % count
                        sounds?.onFocusMoved()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> state.requestFocus(app.key)
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> true // the top of the screen
                    else -> false
                }
            }
            .tvClickable { onOpen(app, item) }
            .focusable(takesFocus),
    ) {
        Crossfade(targetState = item.key, animationSpec = tween(if (reduceMotion) 250 else 700), label = "slide") { key ->
            val born = remember(key) { item }
            Slide(items.firstOrNull { it.key == key } ?: born, metrics, focus = { focus })
        }

        // Open hint, revealed when you step into the shelf (where tvOS shows its Play button).
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = metrics.edge, bottom = metrics.shelfHeight - metrics.firstRowTop + 18.dp)
                .graphicsLayer {
                    alpha = focus
                    translationY = (1f - focus) * 10.dp.toPx()
                }
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xF2FFFFFF))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayMark(Modifier.size(14.dp))
            BasicText(
                if (item.open != null) "Play" else "Open ${app.label}",
                style = Rea.text(16.sp, FontWeight.SemiBold, Rea.Ink),
                maxLines = 1,
            )
        }

        if (count > 1) {
            PageDots(
                count,
                current,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = metrics.shelfHeight - metrics.firstRowTop + 30.dp),
            )
        }
    }
}

@Composable
private fun Slide(item: ShelfItem, metrics: HomeMetrics, focus: () -> Float) {
    val content = LocalShelfContent.current
    val backdrop by produceState(content.peekBackdrop(item), item.key) {
        if (value == null) value = content.backdrop(item)
    }
    // The title waits for its logo rather than flashing text first; no logo means text right away.
    val logo by produceState<LogoState>(
        if (item.logoUrl == null) LogoState.None else content.peekLogo(item)?.let { LogoState.Ready(it) } ?: LogoState.Loading,
        item.key,
    ) {
        if (value == LogoState.Loading) value = content.logo(item)?.let { LogoState.Ready(it) } ?: LogoState.None
    }

    Box(Modifier.fillMaxSize()) {
        val art = backdrop
        val card = item.card
        when {
            art != null -> Image(
                bitmap = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // Faces sit in the upper half of most key art: crop from below.
                alignment = BiasAlignment(0f, -0.5f),
                filterQuality = FilterQuality.Medium,
            )
            card != null -> CardBackdrop(card, metrics)
        }
        // Legibility on the left and the fade into the black behind the rows; stronger than the
        // brand shelf's, since key art is bright.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xD9000000), 0.35f to Color(0x8C000000), 0.62f to Color(0x1F000000), 1f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                // Light enough behind the dock that its glass has art to refract.
                Brush.verticalGradient(0f to Color(0x40000000), 0.28f to Color.Transparent, 0.6f to Color(0x99000000), 0.8f to Color(0xBF000000), 1f to Color.Black),
            ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = metrics.edge, bottom = metrics.shelfHeight - metrics.firstRowTop + 54.dp)
                .width(metrics.width * 0.44f)
                .graphicsLayer { translationY = -focus() * 18.dp.toPx() },
        ) {
            when (val l = logo) {
                is LogoState.Ready -> {
                    val size = logoSize(l.bitmap, metrics, maxHeight = 104.dp)
                    Image(l.bitmap, contentDescription = item.title, modifier = Modifier.size(size), filterQuality = FilterQuality.Medium)
                }
                LogoState.None -> BasicText(
                    item.title,
                    style = Rea.text(44.sp, FontWeight.Bold, shadow = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LogoState.Loading -> Spacer(Modifier.height(80.dp))
            }
            val metadata = item.metadata
            if (metadata.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                BasicText(metadata, style = Rea.text(15.sp, FontWeight.SemiBold, Color(0xD9FFFFFF), shadow = true), maxLines = 1)
            }
            item.progress?.let { p ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(180.dp).height(4.dp).clip(CircleShape).background(Color(0x4DFFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White))
                }
            }
            item.description?.let {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    it,
                    style = Rea.text(16.sp, color = Rea.TextSecondary, shadow = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A recommendation with only its small card image: the card crisp on the right, over itself blurred. */
@Composable
private fun BoxScope.CardBackdrop(card: Bitmap, metrics: HomeMetrics) {
    // Keyed on the bitmap itself: a fresh wrapper each recomposition would re-blur every time.
    val image = remember(card) { card.asImageBitmap() }
    val blurred by produceState<ImageBitmap?>(null, card) {
        value = withContext(Dispatchers.Default) { Blur.downscaleBlur(card, 64, radius = 3).asImageBitmap() }
    }
    blurred?.let {
        Image(
            it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.2f
                scaleY = 1.2f
            },
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
        )
    }
    val h = min(metrics.firstRowTop * 0.52f, 180.dp)
    Image(
        image,
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = metrics.edge, top = metrics.height * 0.11f)
            .size(h * (16f / 9f), h)
            .shadow(28.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .glassEdge(20.dp, strength = 0.8f),
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Medium,
    )
}

/** A solid play triangle with softened corners, as on tvOS's Play buttons. */
@Composable
private fun PlayMark(modifier: Modifier) {
    Canvas(modifier) {
        val inset = size.width * 0.12f
        val path = Path().apply {
            moveTo(size.width * 0.16f, inset)
            lineTo(size.width - inset, size.height / 2f)
            lineTo(size.width * 0.16f, size.height - inset)
            close()
        }
        drawPath(path, Rea.Ink)
        drawPath(path, Rea.Ink, style = Stroke(width = inset * 2f, join = StrokeJoin.Round))
    }
}

private sealed interface LogoState {
    data object Loading : LogoState
    data object None : LogoState
    class Ready(val bitmap: ImageBitmap) : LogoState
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == current) Color.White else Color(0x59FFFFFF)))
        }
    }
}

// --- Brand -----------------------------------------------------------------------------------

@Composable
private fun BrandShelf(app: AppEntry, shelf: ShelfArt, metrics: HomeMetrics, card: DpSize, state: HomeState, onScreen: Boolean) {
    val density = LocalDensity.current
    val art = shelf.art
    val primary = Color(art.primary)
    LaunchedEffect(onScreen, shelf) {
        if (onScreen) state.shelfArt = ShelfGlass(shelf.ambient?.asAndroidBitmap(), art.primary)
    }
    Box(Modifier.fillMaxSize()) {
        val ambient = shelf.ambient
        if (ambient != null) {
            Image(
                bitmap = ambient,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.2f
                    scaleY = 1.2f
                },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(lerp(primary, Color.Black, 0.35f), lerp(primary, Color.Black, 0.85f), Color.Black),
                        center = Offset(with(density) { metrics.width.toPx() }, 0f),
                        radius = with(density) { metrics.width.toPx() } * 1.1f,
                    ),
                ),
            )
        }
        // Legibility on the left, and the fade into the black behind the rows.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xCC000000), 0.5f to Color(0x40000000), 1f to Color(0x1A000000)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x33000000), 0.4f to Color.Transparent, 0.62f to Color(0x99000000), 0.82f to Color(0xCC000000), 1f to Color.Black),
            ),
        )

        val logo = shelf.logo
        if (logo != null) {
            // The logo as a title treatment, bottom-left above the first row.
            val size = logoSize(logo, metrics, maxHeight = 112.dp)
            Image(
                bitmap = logo,
                contentDescription = app.label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = metrics.edge, top = metrics.firstRowTop - 56.dp - size.height)
                    .size(size),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
            )
        } else {
            BasicText(
                app.label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = metrics.edge, top = metrics.firstRowTop - 128.dp)
                    .width(metrics.width * 0.4f),
                style = Rea.text(48.sp, FontWeight.Bold, shadow = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = metrics.edge, top = metrics.height * 0.11f)
                    .size(card)
                    .shadow(28.dp, RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .glassEdge(20.dp, strength = 0.8f),
            ) {
                TileArtwork(app, art)
            }
        }
    }
}

/**
 * Logos are sized to a common area rather than a common width, so a wide wordmark and a stacked
 * mark carry the same visual weight.
 */
private fun logoSize(logo: ImageBitmap, metrics: HomeMetrics, maxHeight: Dp): DpSize {
    val aspect = logo.width / logo.height.toFloat()
    val maxW = metrics.width * 0.34f
    var w = 300.dp * sqrt(80f * aspect / 300f)
    var h = w / aspect
    if (w > maxW) {
        w = maxW
        h = w / aspect
    }
    if (h > maxHeight) {
        h = maxHeight
        w = h * aspect
    }
    return DpSize(w, h)
}
