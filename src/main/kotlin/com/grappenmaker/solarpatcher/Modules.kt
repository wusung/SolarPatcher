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

package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.asm.method.*
import com.grappenmaker.solarpatcher.asm.transform.*
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.config.Constants
import com.grappenmaker.solarpatcher.config.Constants.API
import com.grappenmaker.solarpatcher.config.Constants.defaultAutoGGText
import com.grappenmaker.solarpatcher.config.Constants.defaultCPSText
import com.grappenmaker.solarpatcher.config.Constants.defaultCapesServer
import com.grappenmaker.solarpatcher.config.Constants.defaultFPSText
import com.grappenmaker.solarpatcher.config.Constants.defaultLevelHeadText
import com.grappenmaker.solarpatcher.config.Constants.defaultNickhiderName
import com.grappenmaker.solarpatcher.config.Constants.defaultReachText
import com.grappenmaker.solarpatcher.config.Constants.lcPacketClassname
import com.grappenmaker.solarpatcher.config.Constants.packetClassname
import com.grappenmaker.solarpatcher.config.Constants.runMatcherData
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.reflect.jvm.javaMethod

private const val metadataClass = "lunar/et/llIllIIllIlIlIIIIlIlIllll"
private const val hypixelModsClass = "lunar/bv/llIIIllIIllIIIllIIlIllIIl"
val internalString = getInternalName<String>()

@Serializable
sealed class Module {
    abstract val isEnabled: Boolean
    abstract val className: String
    abstract fun asTransform(): ClassTransform
}

@Serializable
sealed class TextTransformModule : Module() {
    abstract val from: String
    abstract val to: String
    abstract val method: MatcherData
    override fun asTransform() = ClassTransform(className, listOf(TextTransform(method.asMatcher(), from, to)))
}

@Serializable
sealed class RemoveInvokeModule : Module() {
    abstract val method: MatcherData
    abstract val toRemove: MatcherData
    abstract val popCount: Int
    override fun asTransform() = ClassTransform(
        className,
        listOf(RemoveInvokeTransform(method.asMatcher(), toRemove.asMatcher(), popCount))
    )
}

@Serializable
data class Nickhider(
    override val from: String = defaultNickhiderName,
    override val to: String = defaultNickhiderName,
    override val method: MatcherData = MatcherData(
        "IIIlIlIlIIIllIlIIllllllIl",
        "(Z)L$internalString;"
    ),
    override val className: String = "lunar/bF/lllIlIIllllIllIIIlIlIIIll",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class FPS(
    override val from: String = defaultFPSText,
    override val to: String = defaultFPSText,
    override val method: MatcherData = MatcherData(
        "llllllIlIlIIIIIllIIIIIIlI",
        "()L$internalString;"
    ),
    override val className: String = "lunar/bp/llIlIIIllIlllllIllIIIIIlI",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class CPS(
    override val from: String = defaultCPSText,
    override val to: String = defaultCPSText,
    override val method: MatcherData = MatcherData(
        "llllllIlIlIIIIIllIIIIIIlI",
        "()L$internalString;"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bk/llIlIIIllIlllllIllIIIIIlI",
) : TextTransformModule()

@Serializable
data class AutoGG(
    override val from: String = defaultAutoGGText,
    override val to: String = defaultAutoGGText,
    override val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aH/llIllIIllIlIlIIIIlIlIllll;)V"
    ),
    override val className: String = hypixelModsClass,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class LevelHead(
    override val from: String = defaultLevelHeadText,
    override val to: String = defaultLevelHeadText,
    override val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aM/lllllIIlIlIIIIllIllIlllII;)V"
    ),
    override val className: String = hypixelModsClass,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class Freelook(
    override val method: MatcherData = runMatcherData,
    override val isEnabled: Boolean = false,
    override val className: String = metadataClass,
    override val toRemove: MatcherData = MatcherData(
        "llIllIIllIlIlIIIIlIlIllll",
        "(Lcom/google/gson/JsonArray;)V"
    )
) : RemoveInvokeModule() {
    @Transient
    override val popCount = 2
}

@Serializable
data class PinnedServers(
    override val method: MatcherData = runMatcherData,
    override val isEnabled: Boolean = false,
    override val className: String = metadataClass,
    override val toRemove: MatcherData = MatcherData(
        "llIIIllIIllIIIllIIlIllIIl",
        "(Lcom/google/gson/JsonArray;)V"
    )
) : RemoveInvokeModule() {
    @Transient
    override val popCount = 2
}

@Serializable
data class BlogPosts(
    override val method: MatcherData = runMatcherData,
    override val isEnabled: Boolean = false,
    override val className: String = metadataClass,
    override val toRemove: MatcherData = MatcherData("forEach", "(Ljava/util/function/Consumer;)V")
) : RemoveInvokeModule() {
    @Transient
    override val popCount = 2
}

@Serializable
data class ModpacketRemoval(
    override val isEnabled: Boolean = false,
    override val className: String = packetClassname
) : Module() {
    override fun asTransform() = ClassTransform(className, visitors = listOf { parent: ClassVisitor ->
        AdviceClassVisitor(
            parent,
            matchName("addPacket") + matchDescriptor("(ILjava/lang/Class;)V"),
            enterAdvice = {
                loadVariable(0, ILOAD)
                visitIntInsn(BIPUSH, 31)

                val jumpLabel = Label()
                visitJumpInsn(IF_ICMPNE, jumpLabel)
                returnMethod()
                visitLabel(jumpLabel)
            }
        )
    }, shouldExpand = true)
}

@Serializable
data class MantleIntegration(
    override val from: String = defaultCapesServer,
    override val to: String = "capes.mantle.gg",
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/a/llIllIIllIlIlIIIIlIlIllll",
    override val method: MatcherData = MatcherData.CLINIT
) : TextTransformModule()

@Serializable
data class WindowName(
    val to: String = "Lunar Client (Modded by Solar Tweaks)",
    val method: MatcherData = MatcherData("llIIIllIIllIIIllIIlIllIIl", "()L$internalString;"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/as/llIllIIllIlIlIIIIlIlIllll"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(ImplementTransform(method.asMatcher()) {
        visitLdcInsn(to)
        returnMethod(ARETURN)
    }))
}

private const val serverRuleClass = "com/lunarclient/bukkitapi/nethandler/client/obj/ServerRule"

@Serializable
data class NoHitDelay(
    val method: MatcherData = MatcherData("llIIIllIIllIIIllIIlIllIIl", "()Ljava/util/Map;"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/es/llIllIIllIlIlIIIIlIlIllll"
) : Module() {
    override fun asTransform() = ClassTransform(className, visitors = listOf {
        AdviceClassVisitor(it, method.asMatcher(), exitAdvice = { opcode: Int ->
            if (opcode == ARETURN) {
                // No, this is not a redundant dup-pop
                dup()
                visitFieldInsn(GETSTATIC, serverRuleClass, "LEGACY_COMBAT", "L$serverRuleClass;")
                visitInsn(ICONST_1)
                invokeMethod(java.lang.Boolean::class.java.getMethod("valueOf", Boolean::class.javaPrimitiveType))
                invokeMethod(Map::class.java.getMethod("put", Object::class.java, Object::class.java))
                pop()
            }
        })
    }, shouldExpand = true)
}

@Serializable
data class FPSSpoof(
    val multiplier: Double = 2.0,
    val method: MatcherData = MatcherData("llllllIlIlIIIIIllIIIIIIlI", "()L$internalString;"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bp/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(InvokeAdviceTransform(
        method.asMatcher(),
        MatcherData("bridge\$getDebugFPS", "()I").asMatcher(),
        afterAdvice = { spoof(multiplier) }
    )))
}

@Serializable
data class CPSSpoof(
    val chance: Double = .1,
    val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aK/llIllIIllIlIlIIIIlIlIllll;)V"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/dp/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    init {
        require(chance > 0 && chance < 1) { "chance must be >0 and <1" }
    }

    override fun asTransform(): ClassTransform {
        return ClassTransform(className, listOf(InvokeAdviceTransform(
            method.asMatcher(),
            MatcherData("add", "(Ljava/lang/Object;)Z").asMatcher(),
            afterAdvice = {
                // Init label
                val label = Label()

                // Boolean from add
                pop()

                // Load chance, get random double
                visitLdcInsn(chance)
                invokeMethod(ThreadLocalRandom::current)
                invokeMethod(Random::class.java.getMethod("nextDouble"))

                // Check if chance is met, if so, add another
                visitInsn(DCMPG)
                visitJumpInsn(IFLE, label)

                // Call ourselves. Yep, hacky, but idk what else to do
                // because if i were to visit another add method, that would get advice too...
                loadVariable(0)
                loadVariable(1)
                visitMethodInsn(INVOKEVIRTUAL, className, method.name, method.descriptor, false)

                // Done, let's add back a fake boolean, because otherwise
                // the stack height doesn't match
                visitLabel(label)
                visitInsn(ICONST_1)
            }
        )))
    }
}

// Util to multiply by arbitrary double value
// Used for spoofing cps/fps values
private fun MethodVisitor.spoof(multiplier: Double) {
    if (floor(multiplier) == multiplier && multiplier <= 0xFF) {
        // Whole number, can load as byte integer
        visitIntInsn(BIPUSH, multiplier.toInt())

        // Can immediately multiply
        visitInsn(IMUL)
    } else {
        // Can't immediately multiply, should first convert fps/cps to double
        visitInsn(I2D)

        // Should use ldc instruction
        visitLdcInsn(multiplier)

        // Multiply now
        visitInsn(DMUL)

        // Convert back to int
        visitInsn(D2I)
    }
}

@Serializable
data class CustomCommands(
    val commands: Map<String, Command> = mapOf(
        "qb" to Command("/play duels_bridge_duel"),
        "qbd" to Command("/play duels_bridge_doubles"),
        "db" to Command("/duel ", " bridge"),
        "bwp" to Command("/play bedwars_practice")
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/aE/llIllIIllIlIlIIIIlIlIllll",
    val instanceName: String = "llIllIIllIlIlIIIIlIlIllll",
    val registerMethod: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Ljava/lang/Class;Ljava/util/function/Consumer;)Z",
        className,
        ACC_PUBLIC
    ),
    val chatEventClass: String = "lunar/aH/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    companion object {
        // Instance of this class, initialized at runtime
        @JvmStatic
        lateinit var instance: CustomCommands
            private set

        // Function that will return our listener (at runtime),
        // so that we don't have to implement it with bytecode
        @Suppress("Unused") // it is used obviously
        @JvmStatic
        fun handler() = Consumer<Any?> { event ->
            val textField = event.javaClass.fields.first()
            val text = textField[event] as String
            if (!text.startsWith("/")) return@Consumer

            val components = text.split(" ")
            instance.commands[components.first().substring(1)]?.let {
                // Change text sent
                textField[event] = it.prefix + components.drop(1).joinToString(" ") + it.suffix
            }
        }
    }

    override fun asTransform() = ClassTransform(className, visitors = listOf {
        AdviceClassVisitor(it, matchClinit(), exitAdvice = {
            // Set instance of customcommands
            instance = this@CustomCommands

            // Get event bus instance
            visitFieldInsn(GETSTATIC, className, instanceName, "L$className;")

            // Load chat event class onto the stack
            visitLdcInsn(Type.getObjectType(chatEventClass))

            // Load consumer onto the stack
            invokeMethod(
                InvocationType.STATIC,
                "handler",
                "()Ljava/util/function/Consumer;",
                getInternalName<CustomCommands>()
            )

            // Register custom listener
            invokeMethod(InvocationType.VIRTUAL, registerMethod)
        })
    })
}

@Serializable
data class Command(val prefix: String, val suffix: String = "")

@Serializable
data class RPCUpdate(
    val clientID: Long = 920998351430901790,
    val icon: String = "logo",
    val iconText: String = "Solar Tweaks",
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/et/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() = ClassTransform(className, visitors = listOf {
        replaceClassConstants(
            it, mapOf(
                562286213059444737L to clientID,
                "icon_07_11_2020" to icon,
                "Lunar Client" to iconText
            )
        )
    })
}

@Serializable
data class TasklistPrivacy(
    val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/au/llIlIIIllIlllllIllIIIIIlI;)V"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/av/lllIIIllllllIlIIIIIlIIlII"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(StubMethodTransform(method)))
}

@Serializable
data class HostslistPrivacy(
    val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/au/llIlIIIllIlllllIllIIIIIlI;)V"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/av/lllIlIlIlIllIllIIIIlllIII"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(StubMethodTransform(method)))
}

@Serializable
data class UncapReach(
    val method: MatcherData = MatcherData("llllllIlIlIIIIIllIIIIIIlI", "()L$internalString;"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bO/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() =
        ClassTransform(
            className,
            listOf(
                RemoveInvokeTransform(
                    method.asMatcher(),
                    MatcherData("min", "(DD)D").asMatcher()
                )
            ),
            listOf { parent: ClassVisitor -> replaceClassConstants(parent, mapOf(3.0 to null)) }
        )
}

@Serializable
data class RemoveFakeLevelhead(
    override val isEnabled: Boolean = false,
    override val className: String = hypixelModsClass
) : Module() {
    override fun asTransform() = ClassTransform(
        className,
        listOf(
            RemoveInvokeTransform(
                matchAny(),
                ThreadLocalRandom::current.javaMethod!!.asMatcher()
            ),
            ReplaceCodeTransform(
                matchAny(),
                ThreadLocalRandom::class.java.getMethod("nextInt", Int::class.javaPrimitiveType).asMatcher()
            ) { _, _ ->
                pop()
                visitIntInsn(BIPUSH, -26) // Hacky bro
            }
        )
    )
}

@Serializable
data class RemoveHashing(
    val method: MatcherData = MatcherData(
        "WWWWWNNNNWMWWWMWWMMMWMMWM",
        "(L$internalString;[BZ)Z"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "WMMWMWMMWMWWMWMWWWWWWWMMM"
) : Module() {
    override fun asTransform() = ClassTransform(
        className, listOf(ImplementTransform(method.asMatcher()) {
            visitInsn(ICONST_1)
            returnMethod(IRETURN)
        })
    )
}

@Serializable
data class DebugPackets(
    val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aN/llIllIIllIlIlIIIIlIlIllll;)V"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/dG/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() = ClassTransform(
        className, listOf(InvokeAdviceTransform(
            method.asMatcher(),
            matchName("process") + matchOwner(lcPacketClassname),
            afterAdvice = {
                visitPrintln {
                    loadVariable(2)
                    invokeMethod(java.lang.Object::class.java.getMethod("getClass"))
                    invokeMethod(Class<*>::getName)

                    val stringName = internalString
                    concat(
                        "(L$stringName;)L$stringName;",
                        "Received/processed packet type \u0001"
                    )
                }
            }
        ))
    )
}

@Serializable
data class KeystrokesCPS(
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bz/llIlIIIllIlllllIllIIIIIlI",
    override val from: String = defaultCPSText,
    override val to: String = defaultCPSText,
    override val method: MatcherData = MatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/r/lllllIIlIlIIIIllIllIlllII;FFLlunar/dH/lllIlIIllllIllIIIlIlIIIll;Llunar/dH/lllIlIIllllIllIIIlIlIIIll;Llunar/dH/lllIlIIllllIllIIIlIlIIIll;Llunar/dH/lllIlIIllllIllIIIlIlIIIll;Z)V"
    )
) : TextTransformModule()

@Serializable
data class ToggleSprintText(
    val replacements: Map<String, String> = Constants.ToggleSprint.defaultConfig,
    val method: MatcherData = MatcherData.CLINIT,
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/cn/llIlIIIllIlllllIllIIIIIlI\$llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform(): ClassTransform =
        ClassTransform(className, replacements.map { (from, to) -> TextTransform(method.asMatcher(), from, to) })
}

@Serializable
data class ReachText(
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bO/llIlIIIllIlllllIllIIIIIlI",
    override val from: String = defaultReachText,
    override val to: String = defaultReachText,
    override val method: MatcherData = MatcherData("llllllIlIlIIIIIllIIIIIIlI", "()L$internalString;")
) : TextTransformModule()

const val lunarMainClassname = "lunar/as/llIllIIllIlIlIIIIlIlIllll"
const val popupManagerClassname = "lunar/dg/IllIIlIIlIlIllllIIllllIll"
const val notifPacketClassname = "com/lunarclient/bukkitapi/nethandler/client/LCPacketNotification"
const val bridgeClassname = "lunar/eN/IlIIlIllIIllIIIIllIIlIlII"
const val clientBridgeClassname = "lunar/i/llIIIllIIllIIIllIIlIllIIl"
const val serverDataBridgeClassname = "lunar/n/llIIIllIIllIIIllIIlIllIIl"

@Serializable
data class HandleNotifs(
    val getLunarmainMethod: MethodDescription = MethodDescription(
        "IIIIIlIIlIllIlIIlllIlIIIl",
        "()L$lunarMainClassname;",
        lunarMainClassname
    ),
    val getBridgeMethod: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "()L$clientBridgeClassname;",
        bridgeClassname
    ),
    val getServerdataMethod: MethodDescription = MethodDescription(
        "bridge\$getCurrentServerData",
        "()L$serverDataBridgeClassname;",
        clientBridgeClassname
    ),
    val getServerIPMethod: MethodDescription = MethodDescription(
        "bridge\$serverIP",
        "()L$internalString;",
        serverDataBridgeClassname
    ),
    val getPopupManagerMethod: MethodDescription = MethodDescription(
        "lIIlllIlIllIIIIlIIlIlIIll",
        "()L$popupManagerClassname;",
        lunarMainClassname
    ),
    val levelEnum: String = "lunar/dg/IIlllllIllllIlIIllllIlIlI",
    val sendPopupMethod: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(L$levelEnum;L$internalString;L$internalString;)Llunar/dg/IIIlIlIlIIIllIlIIllllllIl;",
        popupManagerClassname
    ),
    val levelmap: Map<String, String> = mapOf(
        "info" to "llIlIIIllIlllllIllIIIIIlI",
        "success" to "llIllIIllIlIlIIIIlIlIllll",
        "warning" to "llIIIllIIllIIIllIIlIllIIl",
        "error" to "lllIlIIllllIllIIIlIlIIIll"
    ),
    override val className: String = "lunar/dG/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    @Transient
    override val isEnabled = true
    fun getLevel(clazz: Class<*>, name: String): Any? = clazz.getField(levelmap[name] ?: "info")[null]

    companion object {
        @JvmStatic
        lateinit var instance: HandleNotifs
    }

    override fun asTransform() = ClassTransform(className, listOf(
        VisitorTransform(matchName("handleNotification")) { parent ->
            object : MethodVisitor(API, parent) {
                init {
                    instance = this@HandleNotifs
                }

                override fun visitCode() {
                    super.visitCode()

                    // Actually implement this garbage
                    // Get "popup manager"
                    invokeMethod(InvocationType.STATIC, getLunarmainMethod)
                    invokeMethod(InvocationType.VIRTUAL, getPopupManagerMethod)

                    // Load level map onto the stack
                    invokeMethod(HandleNotifs::class.java.getMethod("getInstance"))

                    // Load class object onto stack
                    visitLdcInsn(Type.getObjectType(levelEnum))

                    // Load packet level onto the stack
                    loadVariable(1)
                    invokeMethod(
                        InvocationType.VIRTUAL,
                        "getLevel",
                        "()L$internalString;",
                        notifPacketClassname
                    )
                    invokeMethod(java.lang.String::class.java.getMethod("toLowerCase"))

                    // Get level enum constant
                    invokeMethod(HandleNotifs::getLevel)
                    visitTypeInsn(CHECKCAST, levelEnum)

                    // Get current server name
                    invokeMethod(InvocationType.STATIC, getBridgeMethod)
                    invokeMethod(InvocationType.INTERFACE, getServerdataMethod)
                    invokeMethod(InvocationType.INTERFACE, getServerIPMethod)

                    // Load title onto stack
                    concat("(L$internalString;)L$internalString;", "§lNotification - \u0001")

                    // Load message onto operand stack
                    loadVariable(1)
                    invokeMethod(
                        InvocationType.VIRTUAL,
                        "getMessage",
                        "()L$internalString;",
                        notifPacketClassname
                    )

                    // Send message
                    invokeMethod(InvocationType.VIRTUAL, sendPopupMethod)
                    pop()
                }
            }
        }
    ))
}