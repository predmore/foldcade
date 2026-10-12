package app.foldcade.ui

import android.graphics.*
import android.util.Xml
import androidx.core.graphics.PathParser
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

/** Plain resource-pack XML cannot use Android's binary-resource inflater. Decode once off main. */
internal object ThemeVector {
    private data class Element(val tag: String, val attributes: Map<String, String>, val children: List<Element>) {
        fun number(name: String, default: Float = 0f): Float = attributes[name]?.toFloat()?.also {
            require(it.isFinite()) { "$tag: $name must be finite" }
        } ?: default
    }

    fun rasterize(bytes: ByteArray, size: Int): Bitmap {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        while (parser.next() != XmlPullParser.START_TAG) require(parser.eventType != XmlPullParser.END_DOCUMENT) { "Missing vector" }
        val root = read(parser)
        require(root.tag == "vector") { "Expected a vector" }
        val vw = root.number("viewportWidth"); val vh = root.number("viewportHeight")
        require(vw > 0f && vh > 0f) { "Vector viewport must be positive" }
        fun dimension(name: String, fallback: Float): Float = root.attributes[name]?.let {
            require(!it.startsWith('@') && !it.startsWith('?')) { "Vector dimensions must be literal" }
            it.removeSuffix("dp").removeSuffix("dip").removeSuffix("px").toFloat()
        } ?: fallback
        val width = dimension("width", vw); val height = dimension("height", vh)
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f) { "Vector dimensions must be positive" }
        val scale = size.toFloat() / maxOf(width, height)
        val bitmap = Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width / vw, bitmap.height / vh)
        val checkpoint = canvas.saveLayerAlpha(0f, 0f, vw, vh, (root.number("alpha", 1f).coerceIn(0f, 1f) * 255).roundToInt())
        draw(canvas, root.children, 1f)
        canvas.restoreToCount(checkpoint)
        root.attributes["tint"]?.let { tint ->
            Canvas(bitmap).drawColor(color(tint), PorterDuff.Mode.SRC_IN)
        }
        return bitmap
    }

    private fun read(parser: XmlPullParser): Element {
        val tag = parser.name
        val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
        val children = mutableListOf<Element>()
        while (true) when (parser.next()) {
            XmlPullParser.START_TAG -> children += read(parser)
            XmlPullParser.END_TAG -> return Element(tag, attributes, children)
            XmlPullParser.END_DOCUMENT -> error("Unclosed $tag")
        }
    }

    private fun draw(canvas: Canvas, elements: List<Element>, alpha: Float) {
        for (element in elements) when (element.tag) {
            "group" -> {
                val checkpoint = canvas.save()
                val px = element.number("pivotX"); val py = element.number("pivotY")
                canvas.translate(element.number("translateX") + px, element.number("translateY") + py)
                canvas.rotate(element.number("rotation"))
                canvas.scale(element.number("scaleX", 1f), element.number("scaleY", 1f))
                canvas.translate(-px, -py)
                draw(canvas, element.children, alpha)
                canvas.restoreToCount(checkpoint)
            }
            "clip-path" -> canvas.clipPath(path(element))
            "path" -> {
                val path = path(element)
                paint(element, "fillColor", alpha * element.number("fillAlpha", 1f))?.let { canvas.drawPath(path, it) }
                if (element.number("strokeWidth") > 0f) paint(element, "strokeColor", alpha * element.number("strokeAlpha", 1f))?.let {
                    it.style = Paint.Style.STROKE
                    it.strokeWidth = element.number("strokeWidth")
                    it.strokeMiter = element.number("strokeMiterLimit", 4f)
                    it.strokeCap = when (element.attributes["strokeLineCap"]) {
                        "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT
                    }
                    it.strokeJoin = when (element.attributes["strokeLineJoin"]) {
                        "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER
                    }
                    canvas.drawPath(path, it)
                }
            }
            else -> error("Unsupported vector element '${element.tag}'")
        }
    }

    private fun path(element: Element): Path {
        val path = requireNotNull(PathParser.createPathFromPathData(element.attributes["pathData"] ?: error("Missing pathData")))
        path.fillType = if (element.attributes["fillType"] == "evenOdd") Path.FillType.EVEN_ODD else Path.FillType.WINDING
        val start = element.number("trimPathStart"); val end = element.number("trimPathEnd", 1f)
        if (start == 0f && end == 1f) return path
        val offset = element.number("trimPathOffset")
        val measure = PathMeasure(path, false)
        val from = ((start + offset) % 1f) * measure.length
        val to = ((end + offset) % 1f) * measure.length
        return Path().apply {
            if (from > to) {
                measure.getSegment(from, measure.length, this, true)
                measure.getSegment(0f, to, this, true)
            } else measure.getSegment(from, to, this, true)
            rLineTo(0f, 0f)
            fillType = path.fillType
        }
    }

    private fun paint(element: Element, name: String, alpha: Float): Paint? {
        val gradient = element.children.firstOrNull { it.tag == "attr" && it.attributes["name"] == "android:$name" }
            ?.children?.singleOrNull()?.also { require(it.tag == "gradient") { "Expected a gradient" } }
        val literal = element.attributes[name]
        if (gradient == null && literal == null) return null
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (gradient != null) {
                shader = gradient(gradient)
                this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
            } else {
                color = color(requireNotNull(literal))
                this.alpha = (Color.alpha(color) * alpha.coerceIn(0f, 1f)).roundToInt()
            }
        }
    }

    private fun gradient(element: Element): Shader {
        val items = element.children.filter { it.tag == "item" }
        val stops = if (items.isNotEmpty()) items.map { it.number("offset") to color(it.attributes["color"] ?: error("Missing gradient color")) }
        else buildList {
            add(0f to color(element.attributes["startColor"] ?: error("Missing startColor")))
            element.attributes["centerColor"]?.let { add(0.5f to color(it)) }
            add(1f to color(element.attributes["endColor"] ?: error("Missing endColor")))
        }
        require(stops.size >= 2 && stops.all { it.first in 0f..1f } && stops.zipWithNext().all { it.first.first <= it.second.first }) { "Gradient needs increasing offsets in 0..1" }
        val colors = stops.map { it.second }.toIntArray(); val offsets = stops.map { it.first }.toFloatArray()
        val tile = when (element.attributes["tileMode"]) {
            "repeat" -> Shader.TileMode.REPEAT; "mirror" -> Shader.TileMode.MIRROR; else -> Shader.TileMode.CLAMP
        }
        return when (element.attributes["type"]) {
            "radial" -> RadialGradient(element.number("centerX"), element.number("centerY"), element.number("gradientRadius"), colors, offsets, tile)
            "sweep" -> SweepGradient(element.number("centerX"), element.number("centerY"), colors, offsets)
            null, "linear" -> LinearGradient(element.number("startX"), element.number("startY"), element.number("endX"), element.number("endY"), colors, offsets, tile)
            else -> error("Unsupported gradient type")
        }
    }

    private fun color(raw: String): Int {
        require(!raw.startsWith('@') && !raw.startsWith('?')) { "Vector colors must be literal" }
        val expanded = if (raw.startsWith('#') && raw.length in 4..5) "#" + raw.drop(1).flatMap { listOf(it, it) }.joinToString("") else raw
        return Color.parseColor(expanded)
    }
}
