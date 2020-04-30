package com.android.builder.compiling

import com.squareup.javawriter.JavaWriter
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.IOException
import java.util.EnumSet
import javax.lang.model.element.Modifier

/**
 * Stores BuildConfig field information and methods for writing the field to a JavaWriter or
 * ASM ClassWriter.
 */
class BuildConfigField(
        val type: String,
        val name: String,
        val value: Any,
        val comment: String? = null
) {

    @Throws(IOException::class)
    fun emit(writer: ClassWriter) {
        val pfsOpcodes = Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC
        val typeDescriptor = when {
            type == "String" && value is String -> Type.getDescriptor(String::class.java)
            type == "int" && value is Int -> Type.getDescriptor(Int::class.java)
            type == "boolean" && value is Boolean -> Type.getDescriptor(Boolean::class.java)
            else -> fieldTypeNotSupported()
        }
        writer.visitField(pfsOpcodes, name, typeDescriptor, null, value).visitEnd()
    }

    @Throws(IOException::class)
    fun emit(writer: JavaWriter) {
        val publicStaticFinal = EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        if (comment != null) {
            writer.emitSingleLineComment(comment)
        }
        val emitValue: String = if (type == "String" && value is String) {
            if (value.length > 2 && value.first() == '"' && value.last() == '"') {
                value
            } else {
                """"$value""""
            }
        } else if (type == "boolean" && value is Boolean) {
            fieldTypeNotSupported()
        } else {
            value.toString()
        }

        writer.emitField(type, name, publicStaticFinal, emitValue)
    }

    private fun fieldTypeNotSupported(): Nothing {
        throw IllegalArgumentException(
                """BuildConfigField name: $name type: $type and value type: ${value.javaClass
                        .name} cannot be emitted.""".trimMargin())
    }
}


