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

import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.constants
import com.grappenmaker.solarpatcher.asm.matching.ClassMatcher
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching.plus
import com.grappenmaker.solarpatcher.asm.matching.MethodMatcherData
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.match
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchAny
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchClinit
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchDescriptor
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchName
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchOwner
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.plus
import com.grappenmaker.solarpatcher.asm.matching.asMatcher
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
import com.grappenmaker.solarpatcher.config.Constants.packetClassname
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.reflect.jvm.javaMethod

val internalString = getInternalName<String>()

@Serializable
sealed class Module : TransformGenerator {
    abstract val isEnabled: Boolean
}

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
            ClassTransform(listOf(TextTransform(matchAny(), prefix + from, prefix + to))),
            matcher = matcher + {
                from != to && it.constants.filterIsInstance<String>().any { s -> s.contains(from) }
            })

    override fun generate(node: ClassNode) = generator.generate(node)
}

@Serializable
data class Nickhider(
    override val from: String = defaultNickhiderName,
    override val to: String = defaultNickhiderName,
    override val isEnabled: Boolean = false
) : TextTransformModule() {
    override val matcher: ClassMatcher
        get() = matchLunar() + { it.constants.contains("lastKnownHypixelNick") }
}

@Serializable
data class FPS(
    override val prefix: String = "\u0001 ",
    override val from: String = defaultFPSText,
    override val to: String = defaultFPSText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class CPS(
    override val from: String = defaultCPSText,
    override val to: String = defaultCPSText,
    override val isEnabled: Boolean = false,
) : TextTransformModule()

@Serializable
data class AutoGG(
    override val from: String = defaultAutoGGText,
    override val to: String = defaultAutoGGText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class LevelHead(
    override val from: String = defaultLevelHeadText,
    override val to: String = defaultLevelHeadText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

const val initMethodName = "init"

private val metadataMatcher: ClassMatcher = { node ->
    val metaTypes = listOf(
        "blogPosts",
        "modSettings",
        "clientSettings",
        "pinnedServers",
        "serverIntegration",
        "featureFlag",
        "knownServersHash",
        "store"
    )

    node.methods.find { it.name == initMethodName }
        ?.constants?.containsAll(metaTypes) == true
}

@Serializable
data class Metadata(
    val removeCalls: List<String> = listOf(
        "serverIntegration",
        "pinnedServers",
        "modSettings",
        "clientSettings",
        "blogPosts"
    ),
    override val isEnabled: Boolean = false
) : Module(),
    TransformGenerator by matcherGenerator(
        ClassTransform(
            VisitorTransform(matchName(initMethodName)) { parent ->
                object : MethodVisitor(API, parent) {
                    private var lastMetadata: String? = null

                    override fun visitLdcInsn(value: Any?) {
                        super.visitLdcInsn(value)
                        if (value is String) lastMetadata = value
                    }

                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean
                    ) {
                        if (descriptor != "(L$internalString;Ljava/util/function/Consumer;)V" || lastMetadata !in removeCalls) {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }), matcher = metadataMatcher
    )

@Serializable
data class ModpacketRemoval(
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(visitors = listOf { parent: ClassVisitor ->
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
}, shouldExpand = true), matcher = ClassMatching.matchName(packetClassname))

@Serializable
data class MantleIntegration(
    override val from: String = defaultCapesServer,
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

private const val serverRuleClass = "com/lunarclient/bukkitapi/nethandler/client/obj/ServerRule"
private const val logDetection = "No default value for Server Rule [\u0001] found!"

@Serializable
data class NoHitDelay(override val isEnabled: Boolean = false) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains(logDetection) } ?: return null
        return ClassTransform(visitors = listOf {
            AdviceClassVisitor(it, method.asDescription(node).asMatcher(), exitAdvice = { opcode: Int ->
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
}

@Serializable
data class FPSSpoof(
    val multiplier: Double = 2.0,
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(InvokeAdviceTransform(
    matchAny(),
    MethodMatcherData("bridge\$getDebugFPS", "()I").asMatcher(),
    afterAdvice = {
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
)), matcher = { it.constants.contains("[1466 FPS]") })

@Serializable
data class CustomCommands(
    val commands: Map<String, Command> = mapOf(
        "qb" to Command("/play duels_bridge_duel"),
        "qbd" to Command("/play duels_bridge_doubles"),
        "db" to Command("/duel ", " bridge"),
        "bwp" to Command("/play bedwars_practice")
    ),
    val chatEventClass: String = "lunar/aI/IIIlIlllIIlIIIlIllIIlIlll",
    override val isEnabled: Boolean = false
) : Module() {
    init {
        // Yep this is a fake singleton
        // Might want to work on a better "storage" system
        instance = this
    }

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

    override fun generate(node: ClassNode): ClassTransform? {
        if (!node.constants.contains("EventBus [\u0001]: \u0001")) return null

        val className = node.name
        val instanceField = node.fields.find { it.desc.equals("L$className;") } ?: return null
        val registerMethod = node.methods.find {
            it.desc == "(Ljava/lang/Class;Ljava/util/function/Consumer;)Z"
                    && it.instructions.any { insn -> insn is MethodInsnNode && insn.name == "computeIfAbsent" }
        } ?: return null

        return ClassTransform(visitors = listOf {
            AdviceClassVisitor(it, matchClinit(), exitAdvice = {
                // Let's register a try catch block, for the case the mappings aren't up to date
                val start = Label()
                val end = Label()
                visitTryCatchBlock(start, end, end, "java/lang/Throwable")

                // try-start
                visitLabel(start)

                // Get event bus instance
                visitFieldInsn(GETSTATIC, className, instanceField.name, instanceField.desc)

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
                invokeMethod(InvocationType.VIRTUAL, registerMethod.name, registerMethod.desc, className)

                // try-end
                visitLabel(end)

                // try-handler
                visitPrintln(
                    """
                    |Couldn't find outgoing chat packet event!
                    |Try updating your mappings!
                    """.trimMargin()
                )
            })
        })
    }
}

@Serializable
data class Command(val prefix: String, val suffix: String = "")

const val defaultClientID = 562286213059444737L

@Serializable
data class RPCUpdate(
    val clientID: Long = 920998351430901790,
    val icon: String = "logo",
    val iconText: String = "Solar Tweaks",
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(visitors = listOf {
    replaceClassConstants(
        it, mapOf(
            defaultClientID to clientID,
            "icon_07_11_2020" to icon,
            "Lunar Client" to iconText
        )
    )
}), matcher = { it.constants.contains(defaultClientID) })

private fun privacyTransformGenerator(path: String): TransformGenerator = object : TransformGenerator {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains(path) }
        return method?.let { ClassTransform(StubMethodTransform(method.asDescription(node))) }
    }
}

@Serializable
data class TasklistPrivacy(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by privacyTransformGenerator("\u0001\\system32\\tasklist.exe")

@Serializable
data class HostslistPrivacy(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by privacyTransformGenerator("\u0001\\system32\\drivers\\etc\\hosts")

@Serializable
data class UncapReach(override val isEnabled: Boolean = false) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(
        listOf(
            RemoveInvokeTransform(
                matchAny(),
                MethodMatcherData("min", "(DD)D").asMatcher()
            )
        ),
        listOf { parent: ClassVisitor -> replaceClassConstants(parent, mapOf(3.0 to null)) }),
    matcher = { it.constants.containsAll(setOf(" blocks", "range")) }
)

@Serializable
data class RemoveFakeLevelhead(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by matcherGenerator(ClassTransform(
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
        )), matcher = { it.constants.contains("Level: ") }
    )

@Serializable
data class RemoveHashing(
    val className: String = "WMMWMWMMWMWWMWMWWWWWWWMMM",
    val method: MethodMatcherData = MethodMatcherData(
        "WWWWWNNNNWMWWWMWWMMMWMMWM",
        "(L$internalString;[BZ)Z"
    ),
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(
        ImplementTransform(method.asMatcher()) {
            visitInsn(ICONST_1)
            returnMethod(IRETURN)
        }
    ), matcher = ClassMatching.matchName(className)
)

@Serializable
data class DebugPackets(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by matcherGenerator(ClassTransform(
        InvokeAdviceTransform(
            matchAny(),
            matchName("process") + matchOwner(packetClassname),
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
        )), matcher = { it.interfaces.contains("com/lunarclient/bukkitapi/nethandler/client/LCNetHandlerClient") }
    )

@Serializable
data class KeystrokesCPS(
    override val from: String = defaultCPSText,
    override val to: String = defaultCPSText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class ToggleSprintText(
    val replacements: Map<String, String> = Constants.ToggleSprint.defaultConfig,
    val method: MethodMatcherData = MethodMatcherData.CLINIT,
    override val isEnabled: Boolean = false,
) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(replacements.map { (from, to) -> TextTransform(method.asMatcher(), from, to) }),
    matcher = { it.constants.containsAll(replacements.keys) }
)

@Serializable
data class ReachText(
    override val from: String = defaultReachText,
    override val to: String = defaultReachText,
    override val isEnabled: Boolean = false
) : TextTransformModule() {
    override val matcher: ClassMatcher
        get() = matchLunar() + { it.constants.contains("[1.3 blocks]") }
}

const val lunarMainClassname = "lunar/as/llIllIIllIlIlIIIIlIlIllll"
const val popupManagerClassname = "lunar/dg/IllIIlIIlIlIllllIIllllIll"
const val notifPacketClassname = "com/lunarclient/bukkitapi/nethandler/client/LCPacketNotification"

@Serializable
data class HandleNotifs(
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
    val className: String = "lunar/dG/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    @Transient
    override val isEnabled = true
    fun getLevel(clazz: Class<*>, name: String): Any? = clazz.getField(levelmap[name] ?: "info")[null]

    companion object {
        @JvmStatic
        lateinit var instance: HandleNotifs
    }

    override fun generate(node: ClassNode): ClassTransform? {
        if (node.name != className) return null

        return ClassTransform(
            VisitorTransform(matchName("handleNotification")) { parent ->
                object : MethodVisitor(API, parent) {
                    init {
                        instance = this@HandleNotifs
                    }

                    override fun visitCode() {
                        super.visitCode()

                        // Actually implement this garbage
                        // Get "popup manager"
                        invokeMethod(InvocationType.STATIC, RuntimeData.getLunarmainMethod)
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

                        // Load title onto stack
                        visitLdcInsn("§lServer Notification")

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
        )
    }
}

@Serializable
data class ModName(
    val modName: String = "SolarTweaks",
    val className: String = "net/minecraft/client/ClientBrandRetriever"
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(ImplementTransform(matchName("getClientModName")) {
    val solarVersion = "$modName v${Versioning.version}"

    // Add a try catch block, because class access might fail
    val start = Label()
    val end = Label()
    visitTryCatchBlock(start, end, end, "java/lang/Throwable")

    // try-start
    visitLabel(start)

    getObject(RuntimeData::class)
    invokeMethod(RuntimeData::version.getter)
    concat("(L$internalString;)L$internalString;", "Lunar Client \u0001/$solarVersion")
    returnMethod(ARETURN)

    // try-end
    visitLabel(end)

    // try-handler
    dup()
    invokeMethod(Throwable::class.java.getMethod("printStackTrace"))
    visitLdcInsn(solarVersion)
    returnMethod(ARETURN)
}), matcher = ClassMatching.matchName(className)) {
    @Transient
    override val isEnabled: Boolean = true
}

// Utility "module" to get runtime data
object RuntimeData : Module() {
    override val isEnabled = true
    private val knownData = mutableMapOf<String, Any?>()

    lateinit var lunarClientClass: String
    lateinit var getLunarmainMethod: MethodDescription

    val version by runtimeValue("version", "unknown")
    val os by runtimeValue("os", "unknown")
    val arch by runtimeValue("arch", "unknown")

    override fun generate(node: ClassNode): ClassTransform? {
        if (!node.constants.contains("Starting Lunar client...")) return null
        println("Loading runtime values")

        lunarClientClass = node.name
        getLunarmainMethod =
            node.methods.find { Type.getReturnType(it.desc).className == node.name && Modifier.isStatic(it.access) }
                ?.asDescription(node) ?: return null

        val clinit = node.methods.find { matchClinit().match(it.asDescription(node)) } ?: return null
        val assignments = clinit.instructions
            .filterIsInstance<FieldInsnNode>()
            .filter { it.opcode == PUTSTATIC }
            .associate { it.name to (it.previous as? LdcInsnNode)?.cst }

        knownData += node.fields
            .associate { it.name to (it.value ?: assignments[it.name]) }

        // Debuy log information
        println("Runtime information: ${knownData.toList()}")

        return null // Don't perform transformation
    }

    private inline fun <reified T> runtimeValue(field: String, default: T) =
        lazy { (knownData[field] ?: default) as? T ?: error("Wrong type in runtime data!") }
}