package com.dingdangmaoup.resin.pura.resin.version

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode

/** Reads a static String value from class-file structure without defining or executing the class. */
internal object ClassFileStringConstantReader {
    private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
    private const val STATIC_INITIALIZER = "<clinit>"
    private const val STATIC_INITIALIZER_DESCRIPTOR = "()V"

    fun readStaticString(
        classBytes: ByteArray,
        expectedClassName: String,
        fieldName: String,
    ): String? {
        return try {
            val classNode = ClassNode()
            ClassReader(classBytes).accept(classNode, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            if (classNode.name != expectedClassName) return null

            val field = classNode.fields.firstOrNull {
                it.name == fieldName &&
                    it.desc == STRING_DESCRIPTOR &&
                    it.access and Opcodes.ACC_STATIC != 0
            } ?: return null
            (field.value as? String)?.let { return it }

            val initializer = classNode.methods.firstOrNull {
                it.name == STATIC_INITIALIZER && it.desc == STATIC_INITIALIZER_DESCRIPTOR
            } ?: return null
            val assignments = linkedSetOf<String>()
            var instruction: AbstractInsnNode? = initializer.instructions.first
            while (instruction != null) {
                if (instruction is FieldInsnNode &&
                    instruction.opcode == Opcodes.PUTSTATIC &&
                    instruction.owner == expectedClassName &&
                    instruction.name == fieldName &&
                    instruction.desc == STRING_DESCRIPTOR
                ) {
                    val valueInstruction = previousRealInstruction(instruction.previous)
                    val value = (valueInstruction as? LdcInsnNode)?.cst as? String ?: return null
                    assignments.add(value)
                }
                instruction = instruction.next
            }
            assignments.singleOrNull()
        } catch (_: RuntimeException) {
            null
        }
    }

    private tailrec fun previousRealInstruction(instruction: AbstractInsnNode?): AbstractInsnNode? {
        return if (instruction == null || instruction.opcode >= 0) {
            instruction
        } else {
            previousRealInstruction(instruction.previous)
        }
    }
}
