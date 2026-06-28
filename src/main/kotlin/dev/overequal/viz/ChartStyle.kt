package dev.overequal.viz

import dev.overequal.data.Dataset
import org.jetbrains.kotlinx.kandy.letsplot.feature.Layout
import org.jetbrains.kotlinx.kandy.letsplot.settings.font.FontFamily
import org.jetbrains.kotlinx.kandy.letsplot.style.LegendPosition
import org.jetbrains.kotlinx.kandy.letsplot.style.Style
import org.jetbrains.kotlinx.kandy.util.context.invoke

/**
 * Shared Flexoki look for every chart, applied inside a `layout { }` block:
 * paper canvas + panel, ink text in IBM Plex Sans, faint major grid lines, no minor
 * grid, fixed padding around the plotting area, and the legend hidden by default
 * (per-bar frequency gradients carry no legend in the reference). Built on
 * `Style.None` so nothing from a base theme leaks through.
 */
object ChartStyle {
    /** Inner breathing room (px) between the plotting area and the labels/edges. */
    private const val PAD = 28.0

    /** Outer margin (px) around the whole plot, outside the title and axis labels. */
    private const val MARGIN = 32.0

    /** Font sizes (pt) for the plot title, axis titles, and axis tick labels. */
    private const val TITLE_SIZE = 24.0
    private const val AXIS_TITLE_SIZE = 18.0
    private const val AXIS_TEXT_SIZE = 15.0

    fun Layout.flexoki(
        showLegend: Boolean = false,
        blankAxes: Boolean = false,
    ) {
        val fontFamily = FontFamily.custom(Fonts.sans)
        // Pie/donut charts use Style.Void (no axes/grid); everything else builds on
        // Style.None and draws a faint major grid.
        style(if (blankAxes) Style.Void else Style.None) {
            global {
                background {
                    fillColor = Theme.PAPER
                    borderLineColor = Theme.PAPER
                    borderLineWidth = 0.0
                }
                text {
                    color = Theme.BLACK
                    this.fontFamily = fontFamily
                }
                title {
                    this.fontFamily = fontFamily
                    fontSize = TITLE_SIZE
                }
            }
            plotCanvas {
                background {
                    fillColor = Theme.PAPER
                    borderLineColor = Theme.PAPER
                    borderLineWidth = 0.0
                }
                // inset: gap between the plot panel and the axis labels.
                // margin: outer padding around the whole plot, outside the title and labels.
                inset(PAD, PAD, PAD, PAD)
                margin(MARGIN, MARGIN, MARGIN, MARGIN)
            }
            if (blankAxes) {
                // Kandy 0.8.4's pie ignores Style.Void/blankAxes() — the cartesian
                // frame (lines, ticks, numbers, "x"/"y" titles, grid) is still drawn
                // around the donut. Blank each element explicitly (element_blank) so
                // the donut stands alone.
                panel.grid {
                    majorLine { blank = true }
                    minorLine { blank = true }
                }
                axis {
                    line { blank = true }
                    ticks { blank = true }
                    text { blank = true }
                    title { blank = true }
                }
            } else {
                panel.grid {
                    majorLine {
                        color = Theme.GRID
                        width = 0.4
                    }
                    minorLine { blank = true }
                }
                // Lets-Plot defaults the axis lines/ticks to a blue; make them ink.
                axis {
                    line {
                        color = Theme.BLACK
                        width = 0.6
                    }
                    ticks {
                        color = Theme.BLACK
                        width = 0.6
                    }
                    title { fontSize = AXIS_TITLE_SIZE }
                    text { fontSize = AXIS_TEXT_SIZE }
                }
            }
            legend {
                position = if (showLegend) LegendPosition.Right else LegendPosition.None
            }
        }
    }

    /**
     * Apply the title, the dataset's time-period subtitle (so every chart reflects
     * the data's period, per the requirement), size and Flexoki style in one call.
     */
    fun Layout.standard(
        title: String,
        ds: Dataset,
        width: Int = 1100,
        height: Int = 850,
        showLegend: Boolean = false,
        blankAxes: Boolean = false,
    ) {
        this.title = title
        this.subtitle = ds.subtitle()
        this.size = width to height
        flexoki(showLegend, blankAxes)
    }
}
