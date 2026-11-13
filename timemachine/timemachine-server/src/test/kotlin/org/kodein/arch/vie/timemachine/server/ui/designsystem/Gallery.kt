@file:OptIn(ExperimentalTestApi::class)

package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme


/**
 * One component of the design system in every variant worth looking at, as a table: a [GalleryRow] per
 * tone or size, and a cell per state under the [columns]' heads. The gallery draws it in both themes, to
 * be looked at before and after the views move onto it.
 *
 * @property name The name of its renders, `<name>-<theme>.png`: lowercase words joined by `-`, unique among [GalleryScenes].
 * @property columns The head of each column, one per cell of every row; empty when the cells need none.
 * @property cellWidth The width of each cell: a specimen wider than it runs into the next one.
 */
internal class GalleryScene(
    val name: String,
    val rows: List<GalleryRow>,
    val columns: List<String> = emptyList(),
    val cellWidth: Dp = GalleryCellWidth,
) {
    init {
        require(rows.isNotEmpty() && rows.all { it.cells.isNotEmpty() }) { "$name: a scene has rows, and a row has cells" }
        require(columns.isEmpty() || rows.all { it.cells.size == columns.size }) { "$name: every row has a cell per column" }
    }
}

/** One tone or size of a component, [label]led: its [cells] are its specimens, left to right. */
internal class GalleryRow(val label: String, val cells: List<@Composable () -> Unit>) {
    /** A row of a single specimen. */
    constructor(label: String, cell: @Composable () -> Unit) : this(label, listOf(cell))
}

/** The states an interactive specimen is shown in, a column each: what it is given, and the column's head. */
internal enum class SpecimenState(val label: String, val enabled: Boolean) {
    Enabled("enabled", true),
    Disabled("disabled", false),
}

/** A scene with a row per item of [rows] and a column per item of [columns], whose specimens [cell] draws. */
internal fun <R, C> galleryMatrix(
    name: String,
    rows: List<R>,
    columns: List<C>,
    rowLabel: (R) -> String,
    columnLabel: (C) -> String,
    cellWidth: Dp = GalleryCellWidth,
    cell: @Composable (row: R, column: C) -> Unit,
): GalleryScene = GalleryScene(
    name = name,
    rows = rows.map { row ->
        GalleryRow(
            rowLabel(row),
            columns.map { column ->
                val specimen: @Composable () -> Unit = { cell(row, column) }
                specimen
            },
        )
    },
    columns = columns.map(columnLabel),
    cellWidth = cellWidth,
)

/** A cell's width when its scene says none. */
internal val GalleryCellWidth: Dp = 140.dp

/** The height of the window a scene is composed in, in px: a scene must fit in it. */
internal const val GalleryHeight: Int = 2400

/** What the gallery's table is tagged with: what a render captures. */
internal const val GalleryTag: String = "gallery"

/** What the specimen of row [row] in column [column] is tagged with, both from 0. */
internal fun galleryCellTag(row: Int, column: Int): String = "gallery-$row-$column"

private val GalleryPadding = 16.dp
private val GalleryLabelWidth = 160.dp
private val CellGap = 12.dp

private val GalleryScene.cellCount: Int get() = if (columns.isEmpty()) rows.maxOf { it.cells.size } else columns.size
private val GalleryScene.widthDp: Dp get() = GalleryPadding * 2 + GalleryLabelWidth + cellWidth * cellCount

/** How wide the scene's renders are, in px at a density of 1: its margins, its labels and its cells. */
internal val GalleryScene.width: Int get() = widthDp.value.roundToInt()

/** The scene's table, on the panes' surface: its name, the heads of its columns, then a row per [GalleryRow]. */
@Composable
private fun Gallery(scene: GalleryScene) {
    val scheme = MaterialTheme.colorScheme
    val label = InspectorTheme.text.monoMeta
    Column(
        Modifier
            .testTag(GalleryTag)
            .width(scene.widthDp)
            .background(scheme.surfaceContainerLow)
            .padding(GalleryPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(scene.name, style = InspectorTheme.text.monoTitle, color = scheme.onSurface)
        if (scene.columns.isNotEmpty()) Row {
            Spacer(Modifier.width(GalleryLabelWidth))
            for (head in scene.columns) Text(head, style = label, color = scheme.outline, maxLines = 1, modifier = Modifier.width(scene.cellWidth))
        }
        scene.rows.forEachIndexed { r, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label, style = label, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(GalleryLabelWidth).padding(end = 8.dp),
                )
                row.cells.forEachIndexed { c, cell ->
                    Box(Modifier.width(scene.cellWidth).padding(end = CellGap), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.testTag(galleryCellTag(r, c))) { cell() }
                    }
                }
            }
        }
    }
}

/**
 * [scene] composed in the [dark] or the light theme, in a window as wide as the scene and [GalleryHeight]
 * px high, and idle: [block] is given it. Nothing runs off the UI thread.
 */
internal fun gallerySceneTest(scene: GalleryScene, dark: Boolean, block: suspend DesktopComposeUiTest.() -> Unit = {}) =
    runDesktopComposeUiTest(width = scene.width, height = GalleryHeight) {
        setContent { InspectorTheme(dark) { Gallery(scene) } }
        waitForIdle()
        block()
    }

/** [scene] in the [dark] or the light theme, as a PNG of its table: as wide as the scene, as high as its rows. */
internal fun renderGalleryScene(scene: GalleryScene, dark: Boolean): ByteArray {
    lateinit var png: ByteArray
    gallerySceneTest(scene, dark) {
        val image = onNodeWithTag(GalleryTag).captureToImage()
        png = checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes
    }
    return png
}
