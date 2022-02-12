/*
 * Solar Patcher, a runtime patcher for Lunar Client
 * Copyright (C) 2022 Solar Tweaks and respective contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.grappenmaker.solarpatcher.modules

import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.constants
import com.grappenmaker.solarpatcher.asm.matching.ClassMatcher
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching.plus
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching
import com.grappenmaker.solarpatcher.asm.matching.asMatcher
import com.grappenmaker.solarpatcher.asm.transform.*
import com.grappenmaker.solarpatcher.config.Constants
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.tree.ClassNode

@Serializable
sealed class TextTransformModule : Module() {
    @Transient
    open val matcher: ClassMatcher = matchLunar()

    @Transient
    open val prefix: String = ""

    abstract val from: String
    abstract val to: String

    private val generator: TransformGenerator
        get() = matcherGenerator(
            ClassTransform(listOf(TextTransform(MethodMatching.matchAny(), prefix + from, prefix + to))),
            matcher = matcher + {
                from != to && it.constants.filterIsInstance<String>().any { s -> s.contains(from) }
            })

    override fun generate(node: ClassNode) = generator.generate(node)
}

@Serializable
data class Nickhider(
    override val from: String = Constants.defaultNickhiderName,
    override val to: String = Constants.defaultNickhiderName,
    override val isEnabled: Boolean = false
) : TextTransformModule() {
    override val matcher: ClassMatcher
        get() = matchLunar() + { it.constants.contains("lastKnownHypixelNick") }
}

@Serializable
data class FPS(
    override val prefix: String = "\u0001 ",
    override val from: String = Constants.defaultFPSText,
    override val to: String = Constants.defaultFPSText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class CPS(
    override val from: String = Constants.defaultCPSText,
    override val to: String = Constants.defaultCPSText,
    override val isEnabled: Boolean = false,
) : TextTransformModule()

@Serializable
data class AutoGG(
    override val from: String = Constants.defaultAutoGGText,
    override val to: String = Constants.defaultAutoGGText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class LevelHead(
    override val from: String = Constants.defaultLevelHeadText,
    override val to: String = Constants.defaultLevelHeadText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class MantleIntegration(
    override val from: String = Constants.defaultCapesServer,
    override val to: String = "capes.mantle.gg",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class WindowName(
    val from: String = "Lunar Client (\u0001-\u0001/\u0001)",
    val to: String = "Lunar Client (Modded by Solar Tweaks)",
    override val isEnabled: Boolean = false
) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains(from) } ?: return null
        return ClassTransform(ConstantValueTransform(method.asDescription(node).asMatcher(), to))
    }
}

@Serializable
data class KeystrokesCPS(
    override val from: String = Constants.defaultCPSText,
    override val to: String = Constants.defaultCPSText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class ReachText(
    override val from: String = Constants.defaultReachText,
    override val to: String = Constants.defaultReachText,
    override val isEnabled: Boolean = false
) : TextTransformModule() {
    override val matcher: ClassMatcher
        get() = matchLunar() + { it.constants.contains("[1.3 blocks]") }
}