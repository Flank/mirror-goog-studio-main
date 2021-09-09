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

import static com.android.deploy.asm.Opcodes.AALOAD;
import static com.android.deploy.asm.Opcodes.AASTORE;
import static com.android.deploy.asm.Opcodes.ACONST_NULL;
import static com.android.deploy.asm.Opcodes.ANEWARRAY;
import static com.android.deploy.asm.Opcodes.API_VERSION;
import static com.android.deploy.asm.Opcodes.ARETURN;
import static com.android.deploy.asm.Opcodes.ARRAYLENGTH;
import static com.android.deploy.asm.Opcodes.ATHROW;
import static com.android.deploy.asm.Opcodes.BALOAD;
import static com.android.deploy.asm.Opcodes.BASTORE;
import static com.android.deploy.asm.Opcodes.BIPUSH;
import static com.android.deploy.asm.Opcodes.CALOAD;
import static com.android.deploy.asm.Opcodes.CASTORE;
import static com.android.deploy.asm.Opcodes.CHECKCAST;
import static com.android.deploy.asm.Opcodes.D2F;
import static com.android.deploy.asm.Opcodes.D2I;
import static com.android.deploy.asm.Opcodes.D2L;
import static com.android.deploy.asm.Opcodes.DADD;
import static com.android.deploy.asm.Opcodes.DALOAD;
import static com.android.deploy.asm.Opcodes.DASTORE;
import static com.android.deploy.asm.Opcodes.DCMPG;
import static com.android.deploy.asm.Opcodes.DCMPL;
import static com.android.deploy.asm.Opcodes.DCONST_0;
import static com.android.deploy.asm.Opcodes.DCONST_1;
import static com.android.deploy.asm.Opcodes.DDIV;
import static com.android.deploy.asm.Opcodes.DMUL;
import static com.android.deploy.asm.Opcodes.DNEG;
import static com.android.deploy.asm.Opcodes.DREM;
import static com.android.deploy.asm.Opcodes.DRETURN;
import static com.android.deploy.asm.Opcodes.DSUB;
import static com.android.deploy.asm.Opcodes.F2D;
import static com.android.deploy.asm.Opcodes.F2I;
import static com.android.deploy.asm.Opcodes.F2L;
import static com.android.deploy.asm.Opcodes.FADD;
import static com.android.deploy.asm.Opcodes.FALOAD;
import static com.android.deploy.asm.Opcodes.FASTORE;
import static com.android.deploy.asm.Opcodes.FCMPG;
import static com.android.deploy.asm.Opcodes.FCMPL;
import static com.android.deploy.asm.Opcodes.FCONST_0;
import static com.android.deploy.asm.Opcodes.FCONST_1;
import static com.android.deploy.asm.Opcodes.FCONST_2;
import static com.android.deploy.asm.Opcodes.FDIV;
import static com.android.deploy.asm.Opcodes.FMUL;
import static com.android.deploy.asm.Opcodes.FNEG;
import static com.android.deploy.asm.Opcodes.FREM;
import static com.android.deploy.asm.Opcodes.FRETURN;
import static com.android.deploy.asm.Opcodes.FSUB;
import static com.android.deploy.asm.Opcodes.GETFIELD;
import static com.android.deploy.asm.Opcodes.GETSTATIC;
import static com.android.deploy.asm.Opcodes.I2B;
import static com.android.deploy.asm.Opcodes.I2C;
import static com.android.deploy.asm.Opcodes.I2D;
import static com.android.deploy.asm.Opcodes.I2F;
import static com.android.deploy.asm.Opcodes.I2L;
import static com.android.deploy.asm.Opcodes.I2S;
import static com.android.deploy.asm.Opcodes.IADD;
import static com.android.deploy.asm.Opcodes.IALOAD;
import static com.android.deploy.asm.Opcodes.IAND;
import static com.android.deploy.asm.Opcodes.IASTORE;
import static com.android.deploy.asm.Opcodes.ICONST_0;
import static com.android.deploy.asm.Opcodes.ICONST_1;
import static com.android.deploy.asm.Opcodes.ICONST_2;
import static com.android.deploy.asm.Opcodes.ICONST_3;
import static com.android.deploy.asm.Opcodes.ICONST_4;
import static com.android.deploy.asm.Opcodes.ICONST_5;
import static com.android.deploy.asm.Opcodes.ICONST_M1;
import static com.android.deploy.asm.Opcodes.IDIV;
import static com.android.deploy.asm.Opcodes.IFEQ;
import static com.android.deploy.asm.Opcodes.IFGE;
import static com.android.deploy.asm.Opcodes.IFGT;
import static com.android.deploy.asm.Opcodes.IFLE;
import static com.android.deploy.asm.Opcodes.IFLT;
import static com.android.deploy.asm.Opcodes.IFNE;
import static com.android.deploy.asm.Opcodes.IFNONNULL;
import static com.android.deploy.asm.Opcodes.IFNULL;
import static com.android.deploy.asm.Opcodes.IF_ACMPEQ;
import static com.android.deploy.asm.Opcodes.IF_ACMPNE;
import static com.android.deploy.asm.Opcodes.IF_ICMPEQ;
import static com.android.deploy.asm.Opcodes.IF_ICMPGE;
import static com.android.deploy.asm.Opcodes.IF_ICMPGT;
import static com.android.deploy.asm.Opcodes.IF_ICMPLE;
import static com.android.deploy.asm.Opcodes.IF_ICMPLT;
import static com.android.deploy.asm.Opcodes.IF_ICMPNE;
import static com.android.deploy.asm.Opcodes.IINC;
import static com.android.deploy.asm.Opcodes.IMUL;
import static com.android.deploy.asm.Opcodes.INEG;
import static com.android.deploy.asm.Opcodes.INSTANCEOF;
import static com.android.deploy.asm.Opcodes.INVOKEDYNAMIC;
import static com.android.deploy.asm.Opcodes.INVOKEINTERFACE;
import static com.android.deploy.asm.Opcodes.INVOKESPECIAL;
import static com.android.deploy.asm.Opcodes.INVOKESTATIC;
import static com.android.deploy.asm.Opcodes.INVOKEVIRTUAL;
import static com.android.deploy.asm.Opcodes.IOR;
import static com.android.deploy.asm.Opcodes.IREM;
import static com.android.deploy.asm.Opcodes.IRETURN;
import static com.android.deploy.asm.Opcodes.ISHL;
import static com.android.deploy.asm.Opcodes.ISHR;
import static com.android.deploy.asm.Opcodes.ISUB;
import static com.android.deploy.asm.Opcodes.IUSHR;
import static com.android.deploy.asm.Opcodes.IXOR;
import static com.android.deploy.asm.Opcodes.JSR;
import static com.android.deploy.asm.Opcodes.L2D;
import static com.android.deploy.asm.Opcodes.L2F;
import static com.android.deploy.asm.Opcodes.L2I;
import static com.android.deploy.asm.Opcodes.LADD;
import static com.android.deploy.asm.Opcodes.LALOAD;
import static com.android.deploy.asm.Opcodes.LAND;
import static com.android.deploy.asm.Opcodes.LASTORE;
import static com.android.deploy.asm.Opcodes.LCMP;
import static com.android.deploy.asm.Opcodes.LCONST_0;
import static com.android.deploy.asm.Opcodes.LCONST_1;
import static com.android.deploy.asm.Opcodes.LDC;
import static com.android.deploy.asm.Opcodes.LDIV;
import static com.android.deploy.asm.Opcodes.LMUL;
import static com.android.deploy.asm.Opcodes.LNEG;
import static com.android.deploy.asm.Opcodes.LOOKUPSWITCH;
import static com.android.deploy.asm.Opcodes.LOR;
import static com.android.deploy.asm.Opcodes.LREM;
import static com.android.deploy.asm.Opcodes.LRETURN;
import static com.android.deploy.asm.Opcodes.LSHL;
import static com.android.deploy.asm.Opcodes.LSHR;
import static com.android.deploy.asm.Opcodes.LSUB;
import static com.android.deploy.asm.Opcodes.LUSHR;
import static com.android.deploy.asm.Opcodes.LXOR;
import static com.android.deploy.asm.Opcodes.MONITORENTER;
import static com.android.deploy.asm.Opcodes.MONITOREXIT;
import static com.android.deploy.asm.Opcodes.MULTIANEWARRAY;
import static com.android.deploy.asm.Opcodes.NEW;
import static com.android.deploy.asm.Opcodes.NEWARRAY;
import static com.android.deploy.asm.Opcodes.PUTFIELD;
import static com.android.deploy.asm.Opcodes.PUTSTATIC;
import static com.android.deploy.asm.Opcodes.SALOAD;
import static com.android.deploy.asm.Opcodes.SASTORE;
import static com.android.deploy.asm.Opcodes.SIPUSH;
import static com.android.deploy.asm.Opcodes.TABLESWITCH;
import static com.android.deploy.asm.Opcodes.T_BOOLEAN;
import static com.android.deploy.asm.Opcodes.T_BYTE;
import static com.android.deploy.asm.Opcodes.T_CHAR;
import static com.android.deploy.asm.Opcodes.T_DOUBLE;
import static com.android.deploy.asm.Opcodes.T_FLOAT;
import static com.android.deploy.asm.Opcodes.T_INT;
import static com.android.deploy.asm.Opcodes.T_LONG;
import static com.android.deploy.asm.Opcodes.T_SHORT;

import com.android.annotations.NonNull;
import com.android.deploy.asm.Handle;
import com.android.deploy.asm.Type;
import com.android.deploy.asm.tree.AbstractInsnNode;
import com.android.deploy.asm.tree.FieldInsnNode;
import com.android.deploy.asm.tree.IincInsnNode;
import com.android.deploy.asm.tree.IntInsnNode;
import com.android.deploy.asm.tree.JumpInsnNode;
import com.android.deploy.asm.tree.LabelNode;
import com.android.deploy.asm.tree.LdcInsnNode;
import com.android.deploy.asm.tree.LookupSwitchInsnNode;
import com.android.deploy.asm.tree.MethodInsnNode;
import com.android.deploy.asm.tree.MultiANewArrayInsnNode;
import com.android.deploy.asm.tree.TableSwitchInsnNode;
import com.android.deploy.asm.tree.TypeInsnNode;
import com.android.deploy.asm.tree.analysis.AnalyzerException;
import com.android.deploy.asm.tree.analysis.Interpreter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class OpcodeInterpreter extends Interpreter<Value> {

    private final Eval eval;
    private final ByteCodeInterpreter looper;

    public OpcodeInterpreter(Eval eval, @NonNull ByteCodeInterpreter looper) {
        super(API_VERSION);
        this.eval = eval;
        this.looper = looper;
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
                TableSwitchInsnNode ts = (TableSwitchInsnNode) insn;
                int index = value.getInt();
                if (index < ts.min || index > ts.max) {
                    looper.goTo(ts.dflt);
                } else {
                    LabelNode target = ts.labels.get(index - ts.min);
                    looper.goTo(target);
                }
                return null;
            case LOOKUPSWITCH:
                LookupSwitchInsnNode ls = (LookupSwitchInsnNode) insn;
                int v = value.getInt();
                for (int i = 0 ; i < ls.keys.size() ; i++) {
                    if (v == ls.keys.get(i)) {
                        looper.goTo(ls.labels.get(i));
                        return null;
                    }
                }
                looper.goTo(ls.dflt);
                return null;

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
