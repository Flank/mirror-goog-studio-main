/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.tools.debugging.packets

import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants

/**
 * JDWP command constants as specified [here](http://docs.oracle.com/javase/7/docs/platform/jpda/jdwp/jdwp-protocol.html)
 *
 * @see JdwpPacketView.packetCmdSet
 * @see JdwpPacketView.packetCmd
 */
@Suppress("SpellCheckingInspection")
internal object JdwpCommands {

    fun cmdSetToString(cmdSet: Int): String {
        return CmdSet.values().firstOrNull { it.cmdSet == cmdSet }?.name
            ?: String.format("SET_%02X", cmdSet)
    }

    fun cmdToString(cmdSet: Int, cmd: Int): String {
        return CmdSet.values().firstOrNull { it.cmdSet == cmdSet }
            ?.let { cmdSetEnum -> cmdSetEnum.cmdList.firstOrNull { it.cmd == cmd }?.name }
            ?: unknownCommandToString(cmdSet, cmd)
    }

    private fun unknownCommandToString(cmdSet: Int, cmd: Int): String {
        return String.format("CMD_%s_%02X", cmdSetToString(cmdSet).substring(4), cmd)
    }

    enum class CmdSet(val cmdSet: Int, val cmdList: List<Cmd>) {
        SET_VM(1, VmCmd.values().asList()),
        SET_REFTYPE(2, RefTypeCmd.values().asList()),
        SET_CLASSTYPE(3, ClassTypeCmd.values().asList()),
        SET_ARRAYTYPE(4, ArrayTypeCmd.values().asList()),
        SET_INTERFACETYPE(5, InterfaceTypeCmd.values().asList()),
        SET_METHOD(6, MethodCmd.values().asList()),
        SET_FIELD(8, FieldCmd.values().asList()),
        SET_OBJREF(9, ObjRefCmd.values().asList()),
        SET_STRINGREF(10, StringRefCmd.values().asList()),
        SET_THREADREF(11, ThreadRefCmd.values().asList()),
        SET_THREADGROUPREF(12, ThreadGroupRefCmd.values().asList()),
        SET_ARRAYREF(13, ArrayRefCmd.values().asList()),
        SET_CLASSLOADERREF(14, ClassLoaderRefCmd.values().asList()),
        SET_EVENTREQUEST(15, EventRequestCmd.values().asList()),
        SET_STACKFRAME(16, StrackFrameCmd.values().asList()),
        SET_CLASSOBJECTREF(17, ClassObjectRefCmd.values().asList()),
        SET_EVENT(64, EventCmd.values().asList()),

        /** Android Specific */
        SET_DDMS(DdmsPacketConstants.DDMS_CMD_SET, DdmsCmd.values().asList());

        val value: Int
            get() = cmdSet
    }

    enum class VmCmd(override val cmd: Int) : Cmd {
        CMD_VM_VERSION(1),
        CMD_VM_CLASSESBYSIGNATURE(2),
        CMD_VM_ALLCLASSES(3),
        CMD_VM_ALLTHREADS(4),
        CMD_VM_TOPLEVELTHREADGROUPS(5),
        CMD_VM_DISPOSE(6),
        CMD_VM_IDSIZES(7),
        CMD_VM_SUSPEND(8),
        CMD_VM_RESUME(9),
        CMD_VM_EXIT(10),
        CMD_VM_CREATESTRING(11),
        CMD_VM_CAPABILITIES(12),
        CMD_VM_CLASSPATHS(13),
        CMD_VM_DISPOSEOBJECTS(14),
        CMD_VM_HOLDEVENTS(15),
        CMD_VM_RELEASEEVENTS(16),
        CMD_VM_CAPABILITIESNEW(17),
        CMD_VM_REDEFINECLASSES(18),
        CMD_VM_SETDEFAULTSTRATUM(19),
        CMD_VM_ALLCLASSESWITHGENERIC(20),
    }

    enum class RefTypeCmd(override val cmd: Int) : Cmd {
        CMD_REFTYPE_SIGNATURE(1),
        CMD_REFTYPE_CLASSLOADER(2),
        CMD_REFTYPE_MODIFIERS(3),
        CMD_REFTYPE_FIELDS(4),
        CMD_REFTYPE_METHODS(5),
        CMD_REFTYPE_GETVALUES(6),
        CMD_REFTYPE_SOURCEFILE(7),
        CMD_REFTYPE_NESTEDTYPES(8),
        CMD_REFTYPE_STATUS(9),
        CMD_REFTYPE_INTERFACES(10),
        CMD_REFTYPE_CLASSOBJECT(11),
        CMD_REFTYPE_SOURCEDEBUGEXTENSION(12),
        CMD_REFTYPE_SIGNATUREWITHGENERIC(13),
        CMD_REFTYPE_FIELDSWITHGENERIC(14),
        CMD_REFTYPE_METHODSWITHGENERIC(15),
    }

    enum class ClassTypeCmd(override val cmd: Int) : Cmd {
        CMD_CLASSTYPE_SUPERCLASS(1),
        CMD_CLASSTYPE_SETVALUES(2),
        CMD_CLASSTYPE_INVOKEMETHOD(3),
        CMD_CLASSTYPE_NEWINSTANCE(4),
    }

    enum class ArrayTypeCmd(override val cmd: Int) : Cmd {
        CMD_ARRAYTYPE_NEWINSTANCE(1),
    }

    enum class InterfaceTypeCmd(override val cmd: Int) : Cmd {
    }

    enum class MethodCmd(override val cmd: Int) : Cmd {
        CMD_METHOD_LINETABLE(1),
        CMD_METHOD_VARIABLETABLE(2),
        CMD_METHOD_BYTECODES(3),
        CMD_METHOD_ISOBSOLETE(4),
        CMD_METHOD_VARIABLETABLEWITHGENERIC(5),
    }

    enum class FieldCmd(override val cmd: Int) : Cmd {
    }

    enum class ObjRefCmd(override val cmd: Int) : Cmd {
        CMD_OBJREF_REFERENCETYPE(1),
        CMD_OBJREF_GETVALUES(2),
        CMD_OBJREF_SETVALUES(3),
        CMD_OBJREF_MONITORINFO(5),
        CMD_OBJREF_INVOKEMETHOD(6),
        CMD_OBJREF_DISABLECOLLECTION(7),
        CMD_OBJREF_ENABLECOLLECTION(8),
        CMD_OBJREF_ISCOLLECTED(9),
    }

    enum class StringRefCmd(override val cmd: Int) : Cmd {
        CMD_STRINGREF_VALUE(1),
    }

    enum class ThreadRefCmd(override val cmd: Int) : Cmd {
        CMD_THREADREF_NAME(1),
        CMD_THREADREF_SUSPEND(2),
        CMD_THREADREF_RESUME(3),
        CMD_THREADREF_STATUS(4),
        CMD_THREADREF_THREADGROUP(5),
        CMD_THREADREF_FRAMES(6),
        CMD_THREADREF_FRAMECOUNT(7),
        CMD_THREADREF_OWNEDMONITORS(8),
        CMD_THREADREF_CURRENTCONTENDEDMONITOR(9),
        CMD_THREADREF_STOP(10),
        CMD_THREADREF_INTERRUPT(11),
        CMD_THREADREF_SUSPENDCOUNT(12),
    }

    enum class ThreadGroupRefCmd(override val cmd: Int) : Cmd {
        CMD_THREADGROUPREF_NAME(1),
        CMD_THREADGROUPREF_PARENT(2),
        CMD_THREADGROUPREF_CHILDREN(3),
    }

    enum class ArrayRefCmd(override val cmd: Int) : Cmd {
        CMD_ARRAYREF_LENGTH(1),
        CMD_ARRAYREF_GETVALUES(2),
        CMD_ARRAYREF_SETVALUES(3),
    }

    enum class ClassLoaderRefCmd(override val cmd: Int) : Cmd {
        CMD_CLASSLOADERREF_VISIBLECLASSES(1),
    }

    enum class EventRequestCmd(override val cmd: Int) : Cmd {
        CMD_EVENTREQUEST_SET(1),
        CMD_EVENTREQUEST_CLEAR(2),
        CMD_EVENTREQUEST_CLEARALLBREAKPOINTS(3),
    }

    enum class StrackFrameCmd(override val cmd: Int) : Cmd {
        CMD_STACKFRAME_GETVALUES(1),
        CMD_STACKFRAME_SETVALUES(2),
        CMD_STACKFRAME_THISOBJECT(3),
        CMD_STACKFRAME_POPFRAMES(4),
    }

    enum class ClassObjectRefCmd(override val cmd: Int) : Cmd {
        CMD_CLASSOBJECTREF_REFLECTEDTYPE(1),
    }

    enum class EventCmd(override val cmd: Int) : Cmd {
        CMD_EVENT_COMPOSITE(100),
    }

    enum class DdmsCmd(override val cmd: Int) : Cmd {
        CMD_DDMS(DdmsPacketConstants.DDMS_CMD)
    }

    interface Cmd {

        val name: String
        val cmd: Int
        val value: Int
            get() = cmd
    }
}
