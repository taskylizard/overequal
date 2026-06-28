package dev.overequal.viz

import dev.overequal.data.Dataset
import dev.overequal.viz.ChartStyle.standard
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.feature.Position
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.feature.position
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.layers.barsH
import org.jetbrains.kotlinx.kandy.letsplot.layers.pie
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.layers.text
import org.jetbrains.kotlinx.kandy.letsplot.layers.tiles
import org.jetbrains.kotlinx.kandy.letsplot.settings.font.FontFamily
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Reusable Kandy chart builders carrying the Flexoki look. Kept here so each
 * visualization is just data prep + one call.
 */
object Charts {
    /** Point size (pt) for the value labels drawn at the end of bars. */
    private const val VALUE_LABEL_SIZE = 7.0

    /** Estimated glyph width at [VALUE_LABEL_SIZE] used to centre labels, as a fraction of the axis max. */
    private const val CHAR_W_FRAC = 0.013

    /** Looser glyph-width estimate used only to size the axis headroom (avoids right-clipping wide labels). */
    private const val CHAR_W_SAFE_FRAC = 0.020

    /** Gap between a bar tip and the start of its label, as a fraction of the axis max. */
    private const val LABEL_PAD_FRAC = 0.012

    /**
     * Lays out end-of-bar labels. Kandy's text geom has no `hjust`, so a label
     * centred on the bar tip would straddle it (the inner half vanishes against a
     * dark bar). We emulate left-alignment by shifting each label's centre right by
     * half its (estimated) width, and return the axis max needed so the longest
     * label isn't clipped. [barEnds] are the bar tips; [texts] the labels.
     */
    private fun labelLayout(
        barEnds: List<Double>,
        texts: List<String>,
    ): Pair<List<Double>, Double> {
        val max = barEnds.maxOrNull() ?: 0.0
        if (max <= 0.0) return barEnds to 1.0
        val charW = max * CHAR_W_FRAC
        val pad = max * LABEL_PAD_FRAC
        val centers = barEnds.indices.map { barEnds[it] + pad + texts[it].length * charW / 2.0 }
        // Size the axis off a looser width estimate so the widest label has room.
        val safeW = max * CHAR_W_SAFE_FRAC
        val rightEdge = barEnds.indices.maxOf { barEnds[it] + pad + texts[it].length * safeW }
        return centers to maxOf(max, rightEdge) * 1.04
    }

    /**
     * Horizontal bars with per-bar colours. [labels]/[values]/[colors] are given
     * in **top-to-bottom display order** (most important first); the categorical
     * y order is set so the first row sits at the top (config rule 10).
     *
     * Each bar is annotated with its value just past the tip (config: show the
     * actual numbers). Pass [valueLabels] to control the text per bar (aligned to
     * [labels]); otherwise [formatValue] is used (grouped integers, else 1–2 dp).
     */
    fun horizontalBars(
        ds: Dataset,
        title: String,
        xLabel: String,
        labels: List<String>,
        values: List<Double>,
        colors: List<Color>,
        yLabel: String = "",
        valueLabels: List<String>? = null,
        width: Int = 1100,
        height: Int = 900,
    ): Plot {
        // Lets-Plot draws the first y category at the bottom, so reverse to put
        // labels[0] at the top.
        val order = labels.asReversed()
        val texts = valueLabels ?: values.map(::formatValue)
        val (labelX, xMax) = labelLayout(values, texts)
        val data = mapOf("label" to labels, "value" to values)
        return plot(data) {
            barsH {
                y("label") {
                    scale = categorical(categories = order)
                    axis.name = yLabel
                }
                x("value") { axis.name = xLabel }
                fillColor("label") {
                    scale = categorical(*labels.zip(colors).toTypedArray())
                }
            }
            // The trailing (xMax, "") entry is an invisible anchor: a continuous-scale
            // max is ignored here, so we force the axis to reserve room for the widest
            // label (auto-fit only ranges to data points, not rendered text width).
            text {
                y(labels + labels.first(), "ty") {}
                x(labelX + xMax, "tx") {}
                label(texts + "", "tl")
                font.color = Theme.BLACK
                font.size = VALUE_LABEL_SIZE
                font.family = FontFamily.custom(Fonts.sans)
            }
            layout { standard(title, ds, width, height) }
        }
    }

    /** Default bar-label text: grouped integers, else 1 dp (≥10) or 2 dp (<10). */
    fun formatValue(v: Double): String =
        when {
            v == floor(v) && abs(v) < 1e15 -> "%,d".format(v.toLong())
            abs(v) >= 10 -> "%,.1f".format(v)
            else -> "%.2f".format(v)
        }

    /**
     * Vertical bars over a continuous x, coloured by a frequency gradient within
     * one [hue] (darker = larger, config rule 7). Used for the hourly chart and
     * the histograms.
     */
    fun frequencyBars(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        x: List<Double>,
        y: List<Double>,
        hue: Map<Int, Color>,
        width: Int = 1300,
        height: Int = 760,
    ): Plot =
        plot {
            bars {
                x(x) { axis.name = xLabel }
                y(y) { axis.name = yLabel }
                fillColor(y) {
                    scale = continuous(range = hue.getValue(200)..hue.getValue(800))
                }
            }
            layout { standard(title, ds, width, height) }
        }

    /** A line with a faint filled area beneath it (cumulative-over-time charts). */
    fun lineArea(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        x: List<Double>,
        y: List<Double>,
        color: Color,
        width: Int = 1100,
        height: Int = 620,
    ): Plot =
        plot {
            area {
                x(x) {
                    axis.name = xLabel
                    val years = ceil(x.min()).toInt()..floor(x.max()).toInt()
                    if (years.first <= years.last) {
                        axis.breaksLabeled(years.map { it.toDouble() }, years.map { it.toString() })
                    }
                }
                y(y) { axis.name = yLabel }
                fillColor = color
                alpha = 0.12
                borderLine.color = color
                borderLine.width = 2.0
            }
            layout { standard(title, ds, width, height) }
        }

    /** A donut chart with leader-free categorical slices. */
    fun donut(
        ds: Dataset,
        title: String,
        labels: List<String>,
        values: List<Double>,
        colors: List<Color>,
        showLegend: Boolean = true,
        width: Int = 1000,
        height: Int = 900,
    ): Plot =
        plot {
            pie {
                slice(values)
                fillColor(labels) {
                    scale = categorical(*labels.zip(colors).toTypedArray())
                }
                size = 45.0
                hole = 0.55
                stroke = 1.0
                strokeColor = Theme.PAPER
            }
            layout { standard(title, ds, width, height, showLegend, blankAxes = true) }
        }

    /**
     * A scatter plot of points coloured by a single [color]. Pass [labels] (aligned
     * to [x]/[y]) to annotate each dot — the label sits just above its point.
     */
    fun scatter(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        x: List<Double>,
        y: List<Double>,
        color: Color,
        labels: List<String>? = null,
        pointSize: Double = 6.0,
        width: Int = 1000,
        height: Int = 820,
    ): Plot =
        plot {
            points {
                x(x) { axis.name = xLabel }
                y(y) { axis.name = yLabel }
                size = pointSize
                this.color = color
                alpha = 0.85
            }
            if (labels != null && y.isNotEmpty()) {
                // Nudge the label above the dot; the offset point also nudges the
                // y auto-fit up so top labels aren't clipped.
                val span = (y.max() - y.min()).let { if (it > 0) it else 1.0 }
                val dy = span * 0.025
                text {
                    x(x, "lx") {}
                    y(y.map { it + dy }, "ly") {}
                    label(labels, "lt")
                    font.color = Theme.BLACK
                    font.size = VALUE_LABEL_SIZE
                    font.family = FontFamily.custom(Fonts.sans)
                }
            }
            layout { standard(title, ds, width, height) }
        }

    /**
     * Horizontal stacked bars (e.g. slur + profanity rate per member). [labels]
     * are in top-to-bottom order; [seriesOrder] stacks left→right within each bar;
     * [values] maps each series name to its per-label values (aligned to [labels]).
     */
    fun stackedBarsH(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        labels: List<String>,
        seriesOrder: List<String>,
        seriesColors: Map<String, Color>,
        values: Map<String, List<Double>>,
        barLabels: List<String>? = null,
        showLegend: Boolean = true,
        width: Int = 1100,
        height: Int = 900,
    ): Plot {
        val yCol = ArrayList<String>()
        val xCol = ArrayList<Double>()
        val gCol = ArrayList<String>()
        labels.forEachIndexed { i, label ->
            for (s in seriesOrder) {
                yCol.add(label)
                xCol.add(values.getValue(s)[i])
                gCol.add(s)
            }
        }
        val data = mapOf("y" to yCol, "x" to xCol, "g" to gCol)
        val scalePairs = seriesOrder.map { it to seriesColors.getValue(it) }.toTypedArray()
        // Lay the optional end-labels out against each bar's total (stack height).
        val totals = labels.indices.map { i -> seriesOrder.sumOf { values.getValue(it)[i] } }
        val (labelX, xMax) = if (barLabels != null) labelLayout(totals, barLabels) else (totals to 0.0)
        return plot(data) {
            barsH {
                y("y") {
                    scale = categorical(categories = labels.asReversed())
                    axis.name = yLabel
                }
                x("x") { axis.name = xLabel }
                fillColor("g") { scale = categorical(*scalePairs) }
                position = Position.stack()
            }
            if (barLabels != null) {
                // Trailing (xMax, "") entry is an invisible anchor reserving axis room
                // for the widest label (see horizontalBars).
                text {
                    y(labels + labels.first(), "ty") {}
                    x(labelX + xMax, "tx") {}
                    label(barLabels + "", "tl")
                    font.color = Theme.BLACK
                    font.size = VALUE_LABEL_SIZE
                    font.family = FontFamily.custom(Fonts.sans)
                }
            }
            layout { standard(title, ds, width, height, showLegend) }
        }
    }

    /**
     * A heatmap whose cells are coloured by explicit per-cell colours. [xs]/[ys]
     * are parallel cell coordinates (categories) and [cellColors] the matching
     * colours; an identity categorical scale maps each distinct colour to itself.
     */
    fun heatmap(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        xs: List<String>,
        ys: List<String>,
        xOrder: List<String>,
        yOrder: List<String>,
        cellColors: List<Color>,
        width: Int = 1200,
        height: Int = 1100,
    ): Plot {
        val hexes = cellColors.map { (it as org.jetbrains.kotlinx.kandy.util.color.StandardColor.AsHexColor).hexString }
        val distinct = hexes.distinct()
        return plot {
            tiles {
                x(xs) {
                    scale = categorical(categories = xOrder)
                    axis.name = xLabel
                }
                y(ys) {
                    scale = categorical(categories = yOrder)
                    axis.name = yLabel
                }
                fillColor(hexes) {
                    scale = categorical(*distinct.map { it to Color.hex(it) }.toTypedArray())
                }
            }
            layout { standard(title, ds, width, height) }
        }
    }

    /**
     * Stacked layers (bars or area) from long-form data. [x]/[y]/[group] are
     * parallel per-point lists; [groupOrder] sets the stack/legend order (put the
     * most-active group last so it sits on top); [colors] maps group -> colour.
     * [yearAxis] labels a fractional-year x with whole years.
     */
    fun stacked(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        x: List<Double>,
        y: List<Double>,
        group: List<String>,
        groupOrder: List<String>,
        colors: Map<String, Color>,
        asArea: Boolean,
        yearAxis: Boolean = true,
        showLegend: Boolean = true,
        width: Int = 1300,
        height: Int = 820,
    ): Plot {
        val data = mapOf("x" to x, "y" to y, "g" to group)
        val scalePairs = groupOrder.map { it to colors.getValue(it) }.toTypedArray()
        val years = if (x.isEmpty()) IntRange.EMPTY else ceil(x.min()).toInt()..floor(x.max()).toInt()
        return plot(data) {
            if (asArea) {
                area {
                    x("x") {
                        axis.name = xLabel
                        if (yearAxis && !years.isEmpty()) axis.breaksLabeled(years.map { it.toDouble() }, years.map { it.toString() })
                    }
                    y("y") { axis.name = yLabel }
                    fillColor("g") { scale = categorical(*scalePairs) }
                    alpha = 0.9
                    // Separate the bands with a paper hairline (as the donut does).
                    // Lets-Plot otherwise draws the area border in its default blue.
                    borderLine.color = Theme.PAPER
                    borderLine.width = 0.6
                    position = Position.stack()
                }
            } else {
                bars {
                    x("x") {
                        axis.name = xLabel
                        if (yearAxis && !years.isEmpty()) axis.breaksLabeled(years.map { it.toDouble() }, years.map { it.toString() })
                    }
                    y("y") { axis.name = yLabel }
                    fillColor("g") { scale = categorical(*scalePairs) }
                    position = Position.stack()
                }
            }
            layout { standard(title, ds, width, height, showLegend) }
        }
    }

    /**
     * A heatmap coloured by a continuous value gradient within one [hue]
     * (light = small, dark = large). [xs]/[ys]/[values] are parallel per-cell lists.
     */
    fun heatmapValue(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        xs: List<String>,
        ys: List<String>,
        xOrder: List<String>,
        yOrder: List<String>,
        values: List<Double>,
        hue: Map<Int, Color>,
        width: Int = 1200,
        height: Int = 1100,
    ): Plot =
        plot {
            tiles {
                x(xs) {
                    scale = categorical(categories = xOrder)
                    axis.name = xLabel
                }
                y(ys) {
                    scale = categorical(categories = yOrder)
                    axis.name = yLabel
                }
                fillColor(values) {
                    scale = continuous(range = hue.getValue(50)..hue.getValue(900))
                }
            }
            layout { standard(title, ds, width, height) }
        }

    /**
     * A heatmap with a continuous numeric x (e.g. fractional year) and a
     * categorical y, cells coloured by explicit per-cell colours. Used for the
     * weekly activity timeline, where there are too many time buckets to label
     * categorically.
     */
    fun heatmapTimeline(
        ds: Dataset,
        title: String,
        xLabel: String,
        yLabel: String,
        x: List<Double>,
        ys: List<String>,
        yOrder: List<String>,
        cellColors: List<Color>,
        tileWidth: Double,
        width: Int = 1300,
        height: Int = 1000,
    ): Plot {
        val hexes = cellColors.map { (it as org.jetbrains.kotlinx.kandy.util.color.StandardColor.AsHexColor).hexString }
        val distinct = hexes.distinct()
        return plot {
            tiles {
                x(x) {
                    axis.name = xLabel
                    val years = ceil(x.min()).toInt()..floor(x.max()).toInt()
                    if (years.first <= years.last) {
                        axis.breaksLabeled(years.map { it.toDouble() }, years.map { it.toString() })
                    }
                }
                y(ys) {
                    scale = categorical(categories = yOrder)
                    axis.name = yLabel
                }
                this.width = tileWidth
                fillColor(hexes) {
                    scale = categorical(*distinct.map { it to Color.hex(it) }.toTypedArray())
                }
            }
            layout { standard(title, ds, width, height) }
        }
    }
}
