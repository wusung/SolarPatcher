package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.asm.*
import com.grappenmaker.solarpatcher.asm.util.AdviceClassVisitor
import com.grappenmaker.solarpatcher.asm.util.replaceLdcs
import com.grappenmaker.solarpatcher.config.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.reflect.jvm.javaMethod

private const val metadataClass = "lunar/et/llIllIIllIlIlIIIIlIlIllll"

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
    abstract val method: MethodDescription
    override fun asTransform() = ClassTransform(className, listOf(TextTransform(method.asMethodMatcher(), from, to)))
}

@Serializable
sealed class RemoveInvokeModule : Module() {
    abstract val method: MethodDescription
    abstract val toRemove: MethodDescription
    abstract val popCount: Int
    override fun asTransform() = ClassTransform(
        className,
        listOf(RemoveInvokeTransform(method.asMethodMatcher(), toRemove.asMethodMatcher(), popCount))
    )
}

@Serializable
data class Nickhider(
    override val from: String = defaultNickhiderName,
    override val to: String = "BESTWW",
    override val method: MethodDescription = MethodDescription("IIIlIlIlIIIllIlIIllllllIl", "(Z)L${String::class.internalName};"),
    override val className: String = "lunar/bF/lllIlIIllllIllIIIlIlIIIll",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class FPS(
    override val from: String = defaultFPSText,
    override val to: String = "FPM",
    override val method: MethodDescription = MethodDescription("llllllIlIlIIIIIllIIIIIIlI", "()L${String::class.internalName};"),
    override val className: String = "lunar/bp/llIlIIIllIlllllIllIIIIIlI",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class CPS(
    override val from: String = "CPS",
    override val to: String = "CPM",
    override val method: MethodDescription = MethodDescription("llllllIlIlIIIIIllIIIIIIlI", "()L${String::class.internalName};"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bk/llIlIIIllIlllllIllIIIIIlI",
) : TextTransformModule()

@Serializable
data class AutoGG(
    override val from: String = defaultAutoGGText,
    override val to: String = "/ac Good game",
    override val method: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aH/llIllIIllIlIlIIIIlIlIllll;)V"
    ),
    override val className: String = "lunar/bv/llIIIllIIllIIIllIIlIllIIl",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class LevelHead(
    override val from: String = defaultLevelHeadText,
    override val to: String = "IQ: ",
    override val method: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aM/lllllIIlIlIIIIllIllIlllII;)V"
    ),
    override val className: String = "lunar/bv/llIIIllIIllIIIllIIlIllIIl",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class Freelook(
    override val method: MethodDescription = runMethodDescription,
    override val isEnabled: Boolean = false,
    override val className: String = metadataClass,
    override val toRemove: MethodDescription = MethodDescription(
        "llIllIIllIlIlIIIIlIlIllll",
        "(Lcom/google/gson/JsonArray;)V"
    )
) : RemoveInvokeModule() {
    @Transient
    override val popCount = 2
}

@Serializable
data class PinnedServers(
    override val method: MethodDescription = runMethodDescription,
    override val isEnabled: Boolean = false,
    override val className: String = metadataClass,
    override val toRemove: MethodDescription = MethodDescription(
        "llIIIllIIllIIIllIIlIllIIl",
        "(Lcom/google/gson/JsonArray;)V"
    )
) : RemoveInvokeModule() {
    @Transient
    override val popCount = 2
}

@Serializable
data class BlogPosts(
    override val method: MethodDescription = runMethodDescription,
    override val isEnabled: Boolean = false,
    override val className: String = metadataClass,
    override val toRemove: MethodDescription = MethodDescription("forEach", "(Ljava/util/function/Consumer;)V")
) : RemoveInvokeModule() {
    @Transient
    override val popCount = 2
}

@Serializable
data class ModpacketRemoval(
    override val isEnabled: Boolean = true,
    override val className: String = packetClassname
) : Module() {
    override fun asTransform() = ClassTransform(className, visitors = listOf { parent: ClassVisitor ->
        AdviceClassVisitor(
            parent,
            MethodDescription("addPacket", "(ILjava/lang/Class;)V"),
            enterAdvice = {
                loadVariable(0)
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
    override val method: MethodDescription = MethodDescription.CLINIT
) : TextTransformModule()

@Serializable
data class WindowName(
    val to: String = "SolarTweaks",
    val method: MethodDescription = MethodDescription("llIIIllIIllIIIllIIlIllIIl", "()L${String::class.internalName};"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/as/llIllIIllIlIlIIIIlIlIllll"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(ImplementTransform(method.asMethodMatcher()) {
        visitLdcInsn(to)
        returnMethod(ARETURN)
    }))
}

private const val serverRuleClass = "com/lunarclient/bukkitapi/nethandler/client/obj/ServerRule"

@Serializable
data class NoHitDelay(
    val method: MethodDescription = MethodDescription("llIIIllIIllIIIllIIlIllIIl", "()Ljava/util/Map;"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/es/llIllIIllIlIlIIIIlIlIllll"
) : Module() {
    override fun asTransform() = ClassTransform(className, visitors = listOf {
        AdviceClassVisitor(it, method, exitAdvice = { opcode: Int ->
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
    val method: MethodDescription = MethodDescription("llllllIlIlIIIIIllIIIIIIlI", "()L${String::class.internalName};"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bp/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(InvokeAdviceTransform(
        method.asMethodMatcher(),
        MethodDescription("bridge\$getDebugFPS", "()I").asMethodMatcher(),
        afterAdvice = { spoof(multiplier) }
    )))
}

@Serializable
data class CPSSpoof(
    val chance: Double = .1,
    val method: MethodDescription = MethodDescription(
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
            method.asMethodMatcher(),
            MethodDescription("add", "(Ljava/lang/Object;)Z").asMethodMatcher(),
            afterAdvice = {
                // Init label
                val label = Label()

                // Boolean from add
                pop()

                // Load chance, get random double
                visitLdcInsn(chance)
                invokeMethod(ThreadLocalRandom::current.javaMethod!!)
                invokeMethod(java.util.Random::class.java.getMethod("nextDouble"))

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
        className
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
        AdviceClassVisitor(it, MethodDescription.CLINIT, exitAdvice = {
            // Set instance of customcommands
            instance = this@CustomCommands

            // Get event bus instance
            visitFieldInsn(GETSTATIC, className, instanceName, "L$className;")

            // Load chat event class onto the stack
            visitLdcInsn(Type.getObjectType(chatEventClass))

            // Load consumer onto the stack
            invokeMethod(
                InvocationType.STATIC,
                MethodDescription(
                    "handler",
                    "()Ljava/util/function/Consumer;",
                    CustomCommands::class.internalName
                )
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
    override val isEnabled: Boolean = true,
    override val className: String = "lunar/et/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() = ClassTransform(className, visitors = listOf {
        replaceLdcs(
            it, mapOf(
                562286213059444737L to clientID,
                "icon_07_11_2020" to icon,
                "Lunar Client" to iconText
            )
        )
    })
}

@Serializable
data class WebsocketPrivacy(
    val method: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/au/llIlIIIllIlllllIllIIIIIlI;)V"
    ),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/av/lllIIIllllllIlIIIIIlIIlII"
) : Module() {
    override fun asTransform() = ClassTransform(className, listOf(StubMethodTransform(method)))
}

@Serializable
data class UncapReach(
    val method: MethodDescription = MethodDescription("llllllIlIlIIIIIllIIIIIIlI", "()L${String::class.internalName};"),
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bO/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    override fun asTransform() =
        ClassTransform(
            className,
            listOf(
                RemoveInvokeTransform(
                    method.asMethodMatcher(),
                    MethodDescription("min", "(DD)D").asMethodMatcher()
                )
            ),
            listOf { parent: ClassVisitor -> replaceLdcs(parent, mapOf(3.0 to null)) }
        )
}

@Serializable
data class RemoveFakeLevelhead(
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bv/llIIIllIIllIIIllIIlIllIIl"
) : Module() {
    override fun asTransform() = ClassTransform(
        className,
        listOf(
            RemoveInvokeTransform(
                MatchAny,
                ThreadLocalRandom::current.javaMethod!!.asMethodMatcher()
            ),
            ReplaceCodeTransform(
                MatchAny,
                ThreadLocalRandom::class.java.getMethod("nextInt", Int::class.javaPrimitiveType).asMethodMatcher()
            ) { _, _ ->
                pop()
                visitIntInsn(BIPUSH, -26) // Hacky bro
            }
        )
    )
}

@Serializable
data class RemoveHashing(
    override val isEnabled: Boolean = false,
    override val className: String = "WMMWMWMMWMWWMWMWWWWWWWMMM"
) : Module() {
    override fun asTransform() = ClassTransform(
        className, listOf(ImplementTransform(
            MethodDescription("WWWWWNNNNWMWWWMWWMMMWMMWM", "(L${String::class.internalName};[BZ)Z").asMethodMatcher()
        ) {
            visitInsn(ICONST_1)
            returnMethod(IRETURN)
        })
    )
}