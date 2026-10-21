package app.foldcade.language

import kotlin.math.*

/**
 * Compiled, side-effect-free arithmetic. Theme expressions cannot call application code.
 * They evaluate in double precision, so a scene clock that has run for months still
 * moves smoothly; renderers narrow each result to Float at the GPU or Canvas.
 */
fun interface AnimationExpression {
    fun evaluate(values: DoubleArray): Double
    val dependencies: Set<Int> get() = emptySet()
}

internal class CompiledAnimationExpression(
    private val expression: AnimationExpression,
    override val dependencies: Set<Int>,
) : AnimationExpression by expression

/** Symbols resolve once, while loading; frames only read a reusable value array. */
class AnimationSymbols {
    private val names = linkedMapOf<String, Int>()
    private val used = mutableSetOf<String>()
    val size: Int get() = names.size

    /** Every name an expression compiled here reads, so a host can skip inputs nothing reads. */
    val referenced: Set<String> get() = used
    internal fun markUsed(name: String) { used += name }
    fun define(name: String): Int {
        require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid variable '$name'" }
        require(name !in names) { "Duplicate variable '$name'" }
        return names.size.also { names[name] = it }
    }
    fun index(name: String): Int = names[name] ?: error("Unknown variable '$name'")
    fun compile(source: String): AnimationExpression {
        val reader = ExpressionReader(source, this)
        return CompiledAnimationExpression(reader.read(), reader.dependencies)
    }
}

private class ExpressionReader(private val source: String, private val symbols: AnimationSymbols) {
    private var at = 0
    val dependencies = mutableSetOf<Int>()
    fun read(): AnimationExpression {
        val result = expression(0)
        space()
        require(at == source.length) { "Unexpected token at column ${at + 1} in '$source'" }
        return result
    }
    private fun expression(minimum: Int): AnimationExpression {
        var left = atom()
        while (true) {
            space()
            val op = listOf("<=", ">=", "==", "!=", "+", "-", "*", "/", "%", "<", ">")
                .firstOrNull { source.startsWith(it, at) } ?: break
            val precedence = when (op) { "*", "/", "%" -> 3; "+", "-" -> 2; else -> 1 }
            if (precedence < minimum) break
            at += op.length
            val right = expression(precedence + 1)
            val previous = left
            left = AnimationExpression { values ->
                val a = previous.evaluate(values)
                val b = right.evaluate(values)
                when (op) {
                    "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; "%" -> a % b
                    "<" -> flag(a < b); ">" -> flag(a > b); "<=" -> flag(a <= b); ">=" -> flag(a >= b)
                    "==" -> flag(a == b); else -> flag(a != b)
                }
            }
        }
        return left
    }
    private fun atom(): AnimationExpression {
        space()
        if (take('-')) { val e = atom(); return AnimationExpression { -e.evaluate(it) } }
        if (take('+')) return atom()
        if (take('(')) { val e = expression(0); require(take(')')) { "Expected ')' in '$source'" }; return e }
        val start = at
        if (at < source.length && (source[at].isDigit() || source[at] == '.')) {
            while (at < source.length && (source[at].isDigit() || source[at] == '.')) at++
            if (at < source.length && source[at].lowercaseChar() == 'e') {
                at++
                if (at < source.length && source[at] in "+-") at++
                while (at < source.length && source[at].isDigit()) at++
            }
            val number = source.substring(start, at).toDouble()
            require(number.isFinite()) { "Nonfinite literal in '$source'" }
            return AnimationExpression { number }
        }
        while (at < source.length && (source[at].isLetterOrDigit() || source[at] == '_')) at++
        require(at > start) { "Expected expression at column ${at + 1} in '$source'" }
        val name = source.substring(start, at)
        space()
        if (!take('(')) {
            if (name == "pi") return AnimationExpression { PI }
            val slot = symbols.index(name)
            symbols.markUsed(name)
            dependencies += slot
            return AnimationExpression { it[slot] }
        }
        val args = mutableListOf<AnimationExpression>()
        if (!take(')')) {
            do { args += expression(0) } while (take(','))
            require(take(')')) { "Expected ')' after $name in '$source'" }
        }
        val count = when (name) {
            "sin", "cos", "abs", "floor", "ceil", "sqrt", "fract", "smooth" -> 1
            "min", "max", "pow", "hash" -> 2
            "clamp", "mix", "if" -> 3
            "window" -> 4
            else -> error("Unknown function '$name'")
        }
        require(args.size == count) { "$name expects $count arguments in '$source'" }
        return AnimationExpression { values ->
            val a = args[0].evaluate(values)
            when (name) {
                "sin" -> sin(a); "cos" -> cos(a); "abs" -> abs(a); "floor" -> floor(a)
                "ceil" -> ceil(a); "sqrt" -> sqrt(a); "fract" -> a - floor(a); "smooth" -> smooth(a)
                "if" -> args[if (a != 0.0) 1 else 2].evaluate(values)
                else -> {
                    val b = args[1].evaluate(values)
                    when (name) {
                        "min" -> min(a, b); "max" -> max(a, b); "pow" -> a.pow(b); "hash" -> hash(a.toInt(), b.toInt())
                        else -> {
                            val c = args[2].evaluate(values)
                            when (name) {
                                "clamp" -> a.coerceIn(b, c); "mix" -> a + (b - a) * c
                                else -> {
                                    val fade = args[3].evaluate(values)
                                    val along = a - b
                                    when {
                                        along < 0.0 || along > c -> 0.0
                                        along < fade -> smooth(along / fade)
                                        along > c - fade -> smooth((c - along) / fade)
                                        else -> 1.0
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun take(c: Char): Boolean { space(); if (at >= source.length || source[at] != c) return false; at++; return true }
    private fun space() { while (at < source.length && source[at].isWhitespace()) at++ }
}

private fun flag(value: Boolean) = if (value) 1.0 else 0.0
private fun smooth(value: Double): Double { val t = value.coerceIn(0.0, 1.0); return t * t * (3.0 - 2.0 * t) }
private fun hash(turn: Int, salt: Int): Double {
    var mixed = turn * 374761393 + salt * 668265263
    mixed = mixed xor (mixed ushr 13)
    mixed *= 1274126177
    return ((mixed.toLong() and 0x7fffffffL) % 10000L) / 10000.0
}
