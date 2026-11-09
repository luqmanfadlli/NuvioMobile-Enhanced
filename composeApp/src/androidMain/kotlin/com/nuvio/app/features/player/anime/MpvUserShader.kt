package com.nuvio.app.features.player.anime

import kotlin.math.roundToInt

internal data class MpvUserShaderPass(
    val description: String,
    val hook: String,
    val binds: List<String>,
    val save: String?,
    val width: String?,
    val height: String?,
    val condition: String?,
    val body: String,
)

internal data class MpvTextureSize(val width: Int, val height: Int)

internal data class MpvPlannedPass(
    val pass: MpvUserShaderPass,
    val binds: List<String>,
    val target: String,
    val size: MpvTextureSize,
)

internal data class MpvShaderPlan(
    val passes: List<MpvPlannedPass>,
    val outputSize: MpvTextureSize,
)

internal object MpvUserShaderParser {
    private const val DIRECTIVE_PREFIX = "//!"
    private const val MAIN = "MAIN"
    private const val HOOKED = "HOOKED"

    fun parse(source: String): List<MpvUserShaderPass> {
        val passes = mutableListOf<MpvUserShaderPass>()
        var directives = mutableListOf<Pair<String, String>>()
        val body = StringBuilder()

        fun flush() {
            if (directives.isEmpty()) return
            passes += buildPass(directives, body.toString())
            directives = mutableListOf()
            body.setLength(0)
        }

        source.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()
            if (trimmed.startsWith(DIRECTIVE_PREFIX)) {
                if (body.isNotBlank()) flush()
                body.setLength(0)
                val content = trimmed.removePrefix(DIRECTIVE_PREFIX)
                val name = content.substringBefore(' ').trim().uppercase()
                val value = content.substringAfter(' ', "").trim()
                directives += name to value
            } else if (directives.isNotEmpty()) {
                body.append(line).append('\n')
            }
        }
        flush()
        return passes
    }

    private fun buildPass(directives: List<Pair<String, String>>, body: String): MpvUserShaderPass {
        val unsupported = directives.map { it.first }.filterNot { it in SUPPORTED_DIRECTIVES }
        require(unsupported.isEmpty()) { "Unsupported mpv shader directives: $unsupported" }
        val hooks = directives.filter { it.first == "HOOK" }.map { it.second }
        require(hooks.size == 1) { "Expected exactly one HOOK per pass" }
        require(body.contains("hook()")) { "Pass is missing hook()" }
        return MpvUserShaderPass(
            description = directives.lastOrNull { it.first == "DESC" }?.second.orEmpty(),
            hook = hooks.single(),
            binds = directives.filter { it.first == "BIND" }.map { it.second },
            save = directives.lastOrNull { it.first == "SAVE" }?.second,
            width = directives.lastOrNull { it.first == "WIDTH" }?.second,
            height = directives.lastOrNull { it.first == "HEIGHT" }?.second,
            condition = directives.lastOrNull { it.first == "WHEN" }?.second,
            body = body,
        )
    }

    fun plan(
        passes: List<MpvUserShaderPass>,
        input: MpvTextureSize,
        output: MpvTextureSize,
    ): MpvShaderPlan {
        val textures = mutableMapOf(MAIN to input)
        val planned = mutableListOf<MpvPlannedPass>()
        passes.forEach { pass ->
            if (pass.hook != MAIN) return@forEach
            val lookup = { name: String, dimension: Char ->
                val texture = when (name) {
                    HOOKED -> textures[MAIN]
                    "OUTPUT" -> output
                    else -> textures[name]
                }
                texture?.let { if (dimension == 'w') it.width.toFloat() else it.height.toFloat() }
            }
            val condition = pass.condition
            if (condition != null) {
                val result = evaluate(condition, lookup) ?: return@forEach
                if (result == 0f) return@forEach
            }
            val binds = pass.binds.map { if (it == HOOKED) MAIN else it }
            if (binds.any { it !in textures }) return@forEach
            val width = evaluate(pass.width ?: "HOOKED.w", lookup)?.roundToInt() ?: return@forEach
            val height = evaluate(pass.height ?: "HOOKED.h", lookup)?.roundToInt() ?: return@forEach
            if (width <= 0 || height <= 0) return@forEach
            val target = pass.save?.takeUnless { it == HOOKED } ?: MAIN
            val size = MpvTextureSize(width, height)
            textures[target] = size
            planned += MpvPlannedPass(pass = pass, binds = binds.distinct(), target = target, size = size)
        }
        return MpvShaderPlan(passes = planned, outputSize = textures.getValue(MAIN))
    }

    fun evaluate(expression: String, lookup: (String, Char) -> Float?): Float? {
        val stack = ArrayDeque<Float>()
        expression.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { token ->
            when (token) {
                "+", "-", "*", "/", ">", "<", "=" -> {
                    val right = stack.removeLastOrNull() ?: return null
                    val left = stack.removeLastOrNull() ?: return null
                    stack.addLast(
                        when (token) {
                            "+" -> left + right
                            "-" -> left - right
                            "*" -> left * right
                            "/" -> if (right == 0f) return null else left / right
                            ">" -> if (left > right) 1f else 0f
                            "<" -> if (left < right) 1f else 0f
                            else -> if (left == right) 1f else 0f
                        },
                    )
                }
                "!" -> {
                    val value = stack.removeLastOrNull() ?: return null
                    stack.addLast(if (value == 0f) 1f else 0f)
                }
                else -> {
                    val number = token.toFloatOrNull()
                    if (number != null) {
                        stack.addLast(number)
                    } else {
                        val name = token.substringBeforeLast('.', "")
                        val dimension = when (token.substringAfterLast('.')) {
                            "w", "width" -> 'w'
                            "h", "height" -> 'h'
                            else -> return null
                        }
                        if (name.isEmpty()) return null
                        stack.addLast(lookup(name, dimension) ?: return null)
                    }
                }
            }
        }
        return stack.singleOrNull()
    }

    private val SUPPORTED_DIRECTIVES = setOf(
        "DESC",
        "HOOK",
        "BIND",
        "SAVE",
        "WIDTH",
        "HEIGHT",
        "COMPONENTS",
        "WHEN",
    )
}
