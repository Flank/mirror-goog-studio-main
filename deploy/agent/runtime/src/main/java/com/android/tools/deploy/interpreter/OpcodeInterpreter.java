/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.interpreter;

import static org.jetbrains.org.objectweb.asm.Opcodes.AALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.AASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.jetbrains.org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.jetbrains.org.objectweb.asm.Opcodes.API_VERSION;
import static org.jetbrains.org.objectweb.asm.Opcodes.ARETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.jetbrains.org.objectweb.asm.Opcodes.ATHROW;
import static org.jetbrains.org.objectweb.asm.Opcodes.BALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.BASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.BIPUSH;
import static org.jetbrains.org.objectweb.asm.Opcodes.CALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.CASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.CHECKCAST;
import static org.jetbrains.org.objectweb.asm.Opcodes.D2F;
import static org.jetbrains.org.objectweb.asm.Opcodes.D2I;
import static org.jetbrains.org.objectweb.asm.Opcodes.D2L;
import static org.jetbrains.org.objectweb.asm.Opcodes.DADD;
import static org.jetbrains.org.objectweb.asm.Opcodes.DALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.DASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.DCMPG;
import static org.jetbrains.org.objectweb.asm.Opcodes.DCMPL;
import static org.jetbrains.org.objectweb.asm.Opcodes.DCONST_0;
import static org.jetbrains.org.objectweb.asm.Opcodes.DCONST_1;
import static org.jetbrains.org.objectweb.asm.Opcodes.DDIV;
import static org.jetbrains.org.objectweb.asm.Opcodes.DMUL;
import static org.jetbrains.org.objectweb.asm.Opcodes.DNEG;
import static org.jetbrains.org.objectweb.asm.Opcodes.DREM;
import static org.jetbrains.org.objectweb.asm.Opcodes.DRETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.DSUB;
import static org.jetbrains.org.objectweb.asm.Opcodes.F2D;
import static org.jetbrains.org.objectweb.asm.Opcodes.F2I;
import static org.jetbrains.org.objectweb.asm.Opcodes.F2L;
import static org.jetbrains.org.objectweb.asm.Opcodes.FADD;
import static org.jetbrains.org.objectweb.asm.Opcodes.FALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.FASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.FCMPG;
import static org.jetbrains.org.objectweb.asm.Opcodes.FCMPL;
import static org.jetbrains.org.objectweb.asm.Opcodes.FCONST_0;
import static org.jetbrains.org.objectweb.asm.Opcodes.FCONST_1;
import static org.jetbrains.org.objectweb.asm.Opcodes.FCONST_2;
import static org.jetbrains.org.objectweb.asm.Opcodes.FDIV;
import static org.jetbrains.org.objectweb.asm.Opcodes.FMUL;
import static org.jetbrains.org.objectweb.asm.Opcodes.FNEG;
import static org.jetbrains.org.objectweb.asm.Opcodes.FREM;
import static org.jetbrains.org.objectweb.asm.Opcodes.FRETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.FSUB;
import static org.jetbrains.org.objectweb.asm.Opcodes.GETFIELD;
import static org.jetbrains.org.objectweb.asm.Opcodes.GETSTATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.I2B;
import static org.jetbrains.org.objectweb.asm.Opcodes.I2C;
import static org.jetbrains.org.objectweb.asm.Opcodes.I2D;
import static org.jetbrains.org.objectweb.asm.Opcodes.I2F;
import static org.jetbrains.org.objectweb.asm.Opcodes.I2L;
import static org.jetbrains.org.objectweb.asm.Opcodes.I2S;
import static org.jetbrains.org.objectweb.asm.Opcodes.IADD;
import static org.jetbrains.org.objectweb.asm.Opcodes.IALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.IAND;
import static org.jetbrains.org.objectweb.asm.Opcodes.IASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_0;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_1;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_2;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_3;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_4;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_5;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_M1;
import static org.jetbrains.org.objectweb.asm.Opcodes.IDIV;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFEQ;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFGE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFGT;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFLE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFLT;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFNE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFNONNULL;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFNULL;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IINC;
import static org.jetbrains.org.objectweb.asm.Opcodes.IMUL;
import static org.jetbrains.org.objectweb.asm.Opcodes.INEG;
import static org.jetbrains.org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKEDYNAMIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.jetbrains.org.objectweb.asm.Opcodes.IOR;
import static org.jetbrains.org.objectweb.asm.Opcodes.IREM;
import static org.jetbrains.org.objectweb.asm.Opcodes.IRETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.ISHL;
import static org.jetbrains.org.objectweb.asm.Opcodes.ISHR;
import static org.jetbrains.org.objectweb.asm.Opcodes.ISUB;
import static org.jetbrains.org.objectweb.asm.Opcodes.IUSHR;
import static org.jetbrains.org.objectweb.asm.Opcodes.IXOR;
import static org.jetbrains.org.objectweb.asm.Opcodes.JSR;
import static org.jetbrains.org.objectweb.asm.Opcodes.L2D;
import static org.jetbrains.org.objectweb.asm.Opcodes.L2F;
import static org.jetbrains.org.objectweb.asm.Opcodes.L2I;
import static org.jetbrains.org.objectweb.asm.Opcodes.LADD;
import static org.jetbrains.org.objectweb.asm.Opcodes.LALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.LAND;
import static org.jetbrains.org.objectweb.asm.Opcodes.LASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.LCMP;
import static org.jetbrains.org.objectweb.asm.Opcodes.LCONST_0;
import static org.jetbrains.org.objectweb.asm.Opcodes.LCONST_1;
import static org.jetbrains.org.objectweb.asm.Opcodes.LDC;
import static org.jetbrains.org.objectweb.asm.Opcodes.LDIV;
import static org.jetbrains.org.objectweb.asm.Opcodes.LMUL;
import static org.jetbrains.org.objectweb.asm.Opcodes.LNEG;
import static org.jetbrains.org.objectweb.asm.Opcodes.LOOKUPSWITCH;
import static org.jetbrains.org.objectweb.asm.Opcodes.LOR;
import static org.jetbrains.org.objectweb.asm.Opcodes.LREM;
import static org.jetbrains.org.objectweb.asm.Opcodes.LRETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.LSHL;
import static org.jetbrains.org.objectweb.asm.Opcodes.LSHR;
import static org.jetbrains.org.objectweb.asm.Opcodes.LSUB;
import static org.jetbrains.org.objectweb.asm.Opcodes.LUSHR;
import static org.jetbrains.org.objectweb.asm.Opcodes.LXOR;
import static org.jetbrains.org.objectweb.asm.Opcodes.MONITORENTER;
import static org.jetbrains.org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.jetbrains.org.objectweb.asm.Opcodes.MULTIANEWARRAY;
import static org.jetbrains.org.objectweb.asm.Opcodes.NEW;
import static org.jetbrains.org.objectweb.asm.Opcodes.NEWARRAY;
import static org.jetbrains.org.objectweb.asm.Opcodes.PUTFIELD;
import static org.jetbrains.org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.SALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.SASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.SIPUSH;
import static org.jetbrains.org.objectweb.asm.Opcodes.TABLESWITCH;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_BOOLEAN;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_BYTE;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_CHAR;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_DOUBLE;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_FLOAT;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_INT;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_LONG;
import static org.jetbrains.org.objectweb.asm.Opcodes.T_SHORT;

import com.android.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode;
import org.jetbrains.org.objectweb.asm.tree.IincInsnNode;
import org.jetbrains.org.objectweb.asm.tree.IntInsnNode;
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode;
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter;

class OpcodeInterpreter extends Interpreter<Value> {

    private final Eval eval;

    public OpcodeInterpreter(Eval eval) {
        super(API_VERSION);
        this.eval = eval;
    }

    @Override
    public Value newValue(Type type) {
        if (type == null) {
            return Value.NOT_A_VALUE;
        }
        return new NotInitialized(type);
    }

    @Override
    public Value newOperation(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            case ACONST_NULL:
                return Value.NULL_VALUE;
            case ICONST_M1:
                return new IntValue(-1);
            case ICONST_0:
                return new IntValue(0);
            case ICONST_1:
                return new IntValue(1);
            case ICONST_2:
                return new IntValue(2);
            case ICONST_3:
                return new IntValue(3);
            case ICONST_4:
                return new IntValue(4);
            case ICONST_5:
                return new IntValue(5);

            case LCONST_0:
                return new LongValue(0L);
            case LCONST_1:
                return new LongValue(1L);

            case FCONST_0:
                return new FloatValue(0.0f);
            case FCONST_1:
                return new FloatValue(1.0f);
            case FCONST_2:
                return new FloatValue(2.0f);

            case DCONST_0:
                return new DoubleValue(0.0);
            case DCONST_1:
                return new DoubleValue(1.0);

            case BIPUSH:
            case SIPUSH:
                return new IntValue(((IntInsnNode) insn).operand);

            case LDC:
                {
                    final Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Integer) return new IntValue((Integer) cst);
                    if (cst instanceof Float) return new FloatValue((Float) cst);
                    if (cst instanceof Long) return new LongValue((Long) cst);
                    if (cst instanceof Double) return new DoubleValue((Double) cst);
                    if (cst instanceof String) return eval.loadString((String) cst);
                    if (cst instanceof Type) {
                        switch (((Type) cst).getSort()) {
                            case Type.OBJECT:
                            case Type.ARRAY:
                                return eval.loadClass((Type) cst);
                            case Type.METHOD:
                                throw new UnsupportedByteCodeException(
                                        "Method handles are not supported");
                            default:
                                throw new UnsupportedByteCodeException(
                                        "Illegal LDC constant " + cst);
                        }
                    }
                    if (cst instanceof Handle) {
                        throw new UnsupportedByteCodeException("Method handles are not supported");
                    }
                    throw new UnsupportedByteCodeException("Illegal LDC constant " + cst);
                }
            case JSR:
                return new LabelValue(((JumpInsnNode) insn).label);
            case GETSTATIC:
                return eval.getStaticField(new FieldDescription((FieldInsnNode) insn));
            case NEW:
                return eval.newInstance(Type.getObjectType((((TypeInsnNode) insn).desc)));
            default:
                throw new UnsupportedByteCodeException(insn.toString());
        }
    }

    @Override
    public Value copyOperation(AbstractInsnNode node, Value value) {
        return value;
    }

    @Override
    public Value unaryOperation(AbstractInsnNode insn, Value value) throws AnalyzerException {
        switch (insn.getOpcode()) {
            case INEG:
                return new IntValue(-value.getInt());
            case IINC:
                return new IntValue(value.getInt() + (((IincInsnNode) insn).incr));
            case L2I:
                return new IntValue((int) value.getLong());
            case F2I:
                return new IntValue((int) value.getFloat());
            case D2I:
                return new IntValue((int) value.getDouble());
            case I2B:
                return IntValue.fromByte((byte) value.getInt());
            case I2C:
                return IntValue.fromChar((char) value.getInt());
            case I2S:
                return IntValue.fromShort((short) value.getInt());

            case FNEG:
                return new FloatValue(-value.getFloat());
            case I2F:
                return new FloatValue((float) value.getInt());
            case L2F:
                return new FloatValue((float) value.getLong());
            case D2F:
                return new FloatValue((float) value.getDouble());

            case LNEG:
                return new LongValue(-value.getLong());
            case I2L:
                return new LongValue((long) value.getInt());
            case F2L:
                return new LongValue((long) value.getFloat());
            case D2L:
                return new LongValue((long) value.getDouble());

            case DNEG:
                return new DoubleValue(-value.getDouble());
            case I2D:
                return new DoubleValue((double) value.getInt());
            case L2D:
                return new DoubleValue((double) value.getLong());
            case F2D:
                return new DoubleValue((double) value.getFloat());

            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IFNULL:
            case IFNONNULL:
                // Handled by interpreter loop, see checkUnaryCondition()
                return null;

            case TABLESWITCH:
            case LOOKUPSWITCH:
                throw new UnsupportedByteCodeException("Switch is not supported");
            case PUTSTATIC:
                eval.setStaticField(new FieldDescription((FieldInsnNode) insn), value);
                return null;
            case GETFIELD:
                return eval.getField(value, new FieldDescription((FieldInsnNode) insn));
            case NEWARRAY:
                {
                    IntInsnNode intNode = ((IntInsnNode) insn);
                    String typeStr;
                    switch (intNode.operand) {
                        case T_BOOLEAN:
                            typeStr = "[Z";
                            break;
                        case T_BYTE:
                            typeStr = "[B";
                            break;
                        case T_SHORT:
                            typeStr = "[S";
                            break;
                        case T_INT:
                            typeStr = "[I";
                            break;
                        case T_FLOAT:
                            typeStr = "[F";
                            break;
                        case T_DOUBLE:
                            typeStr = "[D";
                            break;
                        case T_LONG:
                            typeStr = "[J";
                            break;
                        case T_CHAR:
                            typeStr = "[C";
                            break;
                        default:
                            throw new AnalyzerException(insn, "Invalid array type");
                    }
                    return eval.newArray(Type.getType(typeStr), value.getInt());
                }
            case ANEWARRAY:
                final String desc = ((TypeInsnNode) insn).desc;
                return eval.newArray(Type.getType("[" + Type.getObjectType(desc)), value.getInt());
            case ARRAYLENGTH:
                return eval.getArrayLength(value);
            case ATHROW:
                // Handled by BytecodeInterpreter
                return null;

            case CHECKCAST:
                {
                    Type targetType = Type.getObjectType(((TypeInsnNode) insn).desc);
                    if (Objects.equals(value, Value.NULL_VALUE)) {
                        return Value.NULL_VALUE;
                    }
                    if (eval.isInstanceOf(value, targetType)) {
                        return new ObjectValue(value.obj(), targetType);
                    }
                    String msg =
                            String.format(
                                    "%s cannot be cast to %s",
                                    value.getAsmType().getClassName(), targetType.getClassName());
                    Exception e = new ClassCastException(msg);
                    throw new LeInterpretingException(e);
                }
            case INSTANCEOF:
                {
                    Type targetType = Type.getObjectType((((TypeInsnNode) insn).desc));
                    return IntValue.fromBool(eval.isInstanceOf(value, targetType));
                }

                // TODO: Implement with JNI
            case MONITORENTER:
            case MONITOREXIT:
                throw new UnsupportedByteCodeException("Monitor are not supported");

            default:
                throw new UnsupportedByteCodeException(insn.toString());
        }
    }

    @Override
    public Value binaryOperation(AbstractInsnNode insn, Value value1, Value value2) {
        switch (insn.getOpcode()) {
            case IALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case FALOAD:
            case LALOAD:
            case DALOAD:
            case AALOAD:
                return eval.getArrayElement(value1, value2);

            case IADD:
                return new IntValue(value1.getInt() + value2.getInt());
            case ISUB:
                return new IntValue(value1.getInt() - value2.getInt());
            case IMUL:
                return new IntValue(value1.getInt() * value2.getInt());
            case IDIV:
                {
                    int divider = value2.getInt();
                    if (divider == 0) {
                        divisionByZero();
                    }
                    return new IntValue(value1.getInt() / divider);
                }
            case IREM:
                {
                    int divider = value2.getInt();
                    if (divider == 0) {
                        divisionByZero();
                    }
                    return new IntValue(value1.getInt() % divider);
                }
            case ISHL:
                return new IntValue(value1.getInt() << value2.getInt());
            case ISHR:
                return new IntValue(value1.getInt() >> value2.getInt());
            case IUSHR:
                return new IntValue(value1.getInt() >>> value2.getInt());
            case IAND:
                return new IntValue(value1.getInt() & value2.getInt());
            case IOR:
                return new IntValue(value1.getInt() | value2.getInt());
            case IXOR:
                return new IntValue(value1.getInt() ^ value2.getInt());

            case LADD:
                return new LongValue(value1.getLong() + value2.getLong());
            case LSUB:
                return new LongValue(value1.getLong() - value2.getLong());
            case LMUL:
                return new LongValue(value1.getLong() * value2.getLong());
            case LDIV:
                {
                    long divider = value2.getLong();
                    if (divider == 0L) {
                        divisionByZero();
                    }
                    return new LongValue(value1.getLong() / divider);
                }
            case LREM:
                {
                    long divider = value2.getLong();
                    if (divider == 0L) {
                        divisionByZero();
                    }
                    return new LongValue(value1.getLong() % divider);
                }
            case LSHL:
                return new LongValue(value1.getLong() << value2.getInt());
            case LSHR:
                return new LongValue(value1.getLong() >> value2.getInt());
            case LUSHR:
                return new LongValue(value1.getLong() >>> value2.getInt());
            case LAND:
                return new LongValue(value1.getLong() & value2.getLong());
            case LOR:
                return new LongValue(value1.getLong() | value2.getLong());
            case LXOR:
                return new LongValue(value1.getLong() ^ value2.getLong());

            case FADD:
                return new FloatValue(value1.getFloat() + value2.getFloat());
            case FSUB:
                return new FloatValue(value1.getFloat() - value2.getFloat());
            case FMUL:
                return new FloatValue(value1.getFloat() * value2.getFloat());
            case FDIV:
                {
                    float divider = value2.getFloat();
                    if (divider == 0f) {
                        divisionByZero();
                    }
                    return new FloatValue(value1.getFloat() / divider);
                }
            case FREM:
                {
                    float divider = value2.getFloat();
                    if (divider == 0f) {
                        divisionByZero();
                    }
                    return new FloatValue(value1.getFloat() % divider);
                }

            case DADD:
                return new DoubleValue(value1.getDouble() + value2.getDouble());
            case DSUB:
                return new DoubleValue(value1.getDouble() - value2.getDouble());
            case DMUL:
                return new DoubleValue(value1.getDouble() * value2.getDouble());
            case DDIV:
                {
                    double divider = value2.getDouble();
                    if (divider == 0.0) {
                        divisionByZero();
                    }
                    return new DoubleValue(value1.getDouble() / divider);
                }
            case DREM:
                {
                    double divider = value2.getDouble();
                    if (divider == 0.0) {
                        divisionByZero();
                    }
                    return new DoubleValue(value1.getDouble() % divider);
                }

            case LCMP:
                {
                    long l1 = value1.getLong();
                    long l2 = value2.getLong();
                    int cmp = -1;
                    if (l1 > l2) cmp = 1;
                    if (l1 == l2) cmp = 0;
                    return new IntValue(cmp);
                }

            case FCMPL:
            case FCMPG:
                {
                    float l1 = value1.getFloat();
                    float l2 = value2.getFloat();

                    int f;
                    if (l1 > l2) {
                        f = 1;
                    } else if (l1 == l2) {
                        f = 0;
                    } else if (l1 < l2) {
                        f = -1;
                    } else {
                        if (insn.getOpcode() == FCMPG) {
                            f = 1;
                        } else {
                            f = -1;
                        }
                    }
                    return new IntValue(f);
                }

            case DCMPL:
            case DCMPG:
                {
                    double l1 = value1.getDouble();
                    double l2 = value2.getDouble();
                    int f;
                    if (l1 > l2) {
                        f = 1;
                    } else if (l1 == l2) {
                        f = 0;
                    } else if (l1 < l2) {
                        f = -1;
                    } else {
                        if (insn.getOpcode() == DCMPG) {
                            f = 1;
                        } else {
                            f = -1;
                        }
                    }
                    return new IntValue(f);
                }

            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
                // Handled by interpreter loop, see checkBinaryCondition()
                return null;
            case PUTFIELD:
                {
                    eval.setField(value1, new FieldDescription((FieldInsnNode) insn), value2);
                    return null;
                }

            default:
                throw new UnsupportedByteCodeException(insn.toString());
        }
    }

    @Override
    public Value ternaryOperation(AbstractInsnNode insn, Value v0, Value v1, Value v2) {
        switch (insn.getOpcode()) {
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
                {
                    eval.setArrayElement(v0, v1, v2);
                    return null;
                }
            default:
                throw new UnsupportedByteCodeException(insn.toString());
        }
    }

    @Override
    public Value naryOperation(AbstractInsnNode insn, List<? extends Value> values) {
        switch (insn.getOpcode()) {
            case MULTIANEWARRAY:
                {
                    MultiANewArrayInsnNode node = (MultiANewArrayInsnNode) insn;
                    List<Integer> args =
                            values.stream().map(Value::getInt).collect(Collectors.toList());
                    return eval.newMultiDimensionalArray(Type.getType(node.desc), args);
                }

            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKEINTERFACE:
                {
                    return eval.invokeMethod(
                            values.get(0),
                            new MethodDescription((MethodInsnNode) insn),
                            values.subList(1, values.size()),
                            insn.getOpcode() == INVOKESPECIAL);
                }

            case INVOKESTATIC:
                return eval.invokeStaticMethod(
                        new MethodDescription((MethodInsnNode) insn), values);
            case INVOKEDYNAMIC:
                throw new UnsupportedByteCodeException("INDY is not supported");
            default:
                throw new UnsupportedByteCodeException(insn.toString());
        }
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, Value value, Value v1) {
        switch (insn.getOpcode()) {
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
                // Handled by interpreter loop
                return;
            default:
                throw new UnsupportedByteCodeException(insn.toString());
        }
    }

    @Override
    public Value merge(Value value, Value w) {
        return w;
    }

    boolean checkUnaryCondition(@NonNull Value value, int opcode) {
        switch (opcode) {
            case IFEQ:
                return value.getInt() == 0;
            case IFNE:
                return value.getInt() != 0;
            case IFLT:
                return value.getInt() < 0;
            case IFGT:
                return value.getInt() > 0;
            case IFLE:
                return value.getInt() <= 0;
            case IFGE:
                return value.getInt() >= 0;
            case IFNULL:
                return value.obj() == null;
            case IFNONNULL:
                return value.obj() != null;
            default:
                throw new UnsupportedByteCodeException("Unknown opcode: " + opcode);
        }
    };

    private static class UnsupportedByteCodeException extends RuntimeException {
        UnsupportedByteCodeException(@NonNull String msg) {
            super(msg);
        }
    };

    boolean checkBinaryCondition(@NonNull Value value1, @NonNull Value value2, int opcode) {
        switch (opcode) {
            case IF_ICMPEQ:
                return value1.getInt() == value2.getInt();
            case IF_ICMPNE:
                return value1.getInt() != value2.getInt();
            case IF_ICMPLT:
                return value1.getInt() < value2.getInt();
            case IF_ICMPGT:
                return value1.getInt() > value2.getInt();
            case IF_ICMPLE:
                return value1.getInt() <= value2.getInt();
            case IF_ICMPGE:
                return value1.getInt() >= value2.getInt();
            case IF_ACMPEQ:
                return Objects.equals(value1.obj(), value2.obj());
            case IF_ACMPNE:
                return !Objects.equals(value1.obj(), value2.obj());
            default:
                throw new UnsupportedByteCodeException("Unknown opcode: " + opcode);
        }
    };

    private static class NotInitialized extends Value {
        NotInitialized(@NonNull Type asmType) {
            super(asmType, false);
        }

        @Override
        public String toString() {
            return "NotInitialized: " + asmType;
        }
    };

    private void divisionByZero() {
        throw new LeInterpretingException(new ArithmeticException("Division by zero"));
    };

    private static class LeInterpretingException extends RuntimeException {
        LeInterpretingException(@NonNull Throwable cause) {
            super(cause);
        }
    }
}
