package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.asm.*
import com.grappenmaker.solarpatcher.asm.util.AdviceClassVisitor
import com.grappenmaker.solarpatcher.config.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import kotlin.math.floor

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
    override fun asTransform() = ClassTransform(className, listOf(TextTransform(method, from, to)))
}

@Serializable
sealed class RemoveInvokeModule : Module() {
    abstract val method: MethodDescription
    abstract val toRemove: MethodDescription
    abstract val popCount: Int
    override fun asTransform() = ClassTransform(className, listOf(RemoveInvokeTransform(method, toRemove, popCount)))
}

@Serializable
data class Nickhider(
    override val from: String = defaultNickhiderName,
    override val to: String = "BESTWW",
    override val method: MethodDescription = MethodDescription("IIIlIlIlIIIllIlIIllllllIl", "(Z)Ljava/lang/String;"),
    override val className: String = "lunar/bF/lllIlIIllllIllIIIlIlIIIll",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class FPS(
    override val from: String = defaultFPSText,
    override val to: String = "FPM",
    override val method: MethodDescription = MethodDescription("llllllIlIlIIIIIllIIIIIIlI", "()Ljava/lang/String;"),
    override val className: String = "lunar/bp/llIlIIIllIlllllIllIIIIIlI",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class AutoGG(
    override val from: String = defaultAutoGGText,
    override val to: String = "Good game",
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
                // Debug log
                visitPrint("Adding packet id ")
                visitPrint {
                    loadVariable(0)
                    invokeMethod(Integer::class.java.getMethod("toString"))
                }
                visitPrint("\n")

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
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/as/llIllIIllIlIlIIIIlIlIllll"
) : Module() {
    private val method = MethodDescription("llIIIllIIllIIIllIIlIllIIl", "()Ljava/lang/String;")
    override fun asTransform() = ClassTransform(className, listOf(ImplementTransform(method) {
        visitLdcInsn(to)
        returnMethod(ARETURN)
    }))
}

private const val serverRuleClass = "com/lunarclient/bukkitapi/nethandler/client/obj/ServerRule"

@Serializable
data class NoHitDelay(
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/es/llIllIIllIlIlIIIIlIlIllll"
) : Module() {
    private val method = MethodDescription("llIIIllIIllIIIllIIlIllIIl", "()Ljava/util/Map;")
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
    override val isEnabled: Boolean = false,
    override val className: String = "lunar/bp/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    private val method = MethodDescription("llllllIlIlIIIIIllIIIIIIlI", "()Ljava/lang/String;")
    override fun asTransform() = ClassTransform(className, listOf(InvokeAdviceTransform(
        method,
        MethodDescription("bridge\$getDebugFPS", "()I"),
        afterAdvice = {
            // Load multiplier after getting fps
            if (floor(multiplier) == multiplier && multiplier <= 0xFF) {
                // Whole number, can load as byte integer
                visitIntInsn(BIPUSH, multiplier.toInt())

                // Can immediately multiply
                visitInsn(IMUL)
            } else {
                // Can't immediately multiply, should first convert fps to double
                visitInsn(I2D)

                // Should use ldc instruction
                visitLdcInsn(multiplier)

                // Multiply now
                visitInsn(DMUL)

                // Convert back to int
                visitInsn(D2I)
            }
        }
    )))
}