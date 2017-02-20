package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.LintUtils.getNextInstruction;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.detector.api.ClassContext;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

/**
 * Utility methods for checking whether a given element is surrounded (or preceded!) by
 * an API check using SDK_INT (or other version checking utilities such as BuildCompat#isAtLeastN)
 */
public class VersionChecks {
    private interface ApiLevelLookup {
        int getApiLevel(@NonNull PsiElement element);
    }

    public static final String SDK_INT = "SDK_INT";
    private static final String ANDROID_OS_BUILD_VERSION = "android/os/Build$VERSION";

    public static int codeNameToApi(@NonNull String text) {
        int dotIndex = text.lastIndexOf('.');
        if (dotIndex != -1) {
            text = text.substring(dotIndex + 1);
        }

        return SdkVersionInfo.getApiByBuildCode(text, true);
    }

    public static boolean isWithinSdkConditional(
            @NonNull ClassContext context,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method,
            @NonNull AbstractInsnNode call,
            int requiredApi) {
        assert requiredApi != -1;

        if (!containsSimpleSdkCheck(method)) {
            return false;
        }

        try {
            // Search in the control graph, from beginning, up to the target call
            // node, to see if it's reachable. The call graph is constructed in a
            // special way: we include all control flow edges, *except* those that
            // are satisfied by a SDK_INT version check (where the operand is a version
            // that is at least as high as the one needed for the given call).
            //
            // If we can reach the call, that means that there is a way this call
            // can be reached on some versions, and lint should flag the call/field lookup.
            //
            //
            // Let's say you have code like this:
            //   if (SDK_INT >= LOLLIPOP) {
            //       // Call
            //       return property.hasAdjacentMapping();
            //   }
            //   ...
            //
            // The compiler will turn this into the following byte code:
            //
            //    0:    getstatic #3; //Field android/os/Build$VERSION.SDK_INT:I
            //    3:    bipush 21
            //    5:    if_icmple 17
            //    8:    aload_1
            //    9:    invokeinterface	#4, 1; //InterfaceMethod
            //                       android/view/ViewDebug$ExportedProperty.hasAdjacentMapping:()Z
            //    14:   ifeq 17
            //    17:   ... code after if loop
            //
            // When the call graph is constructed, for an if branch we're called twice; once
            // where the target is the next instruction (the one taken if byte code check is false)
            // and one to the jump label (the one taken if the byte code condition is true).
            //
            // Notice how at the byte code level, the logic is reversed: the >= instruction
            // is turned into "<" and we jump to the code *after* the if clause; otherwise
            // it will just fall through. Therefore, if we take a byte code branch, that means
            // that the SDK check was *not* satisfied, and conversely, the target call is reachable
            // if we don't take the branch.
            //
            // Therefore, when we build the call graph, we will add call graph nodes for an
            // if check if :
            //   (1) it is some other comparison than <, <= or !=.
            //   (2) if the byte code comparison check is *not* satisfied, this means that the the
            //       SDK check was successful and that the call graph should only include
            //       the jump edge
            //   (3) all other edges are added
            //
            // With a flow control graph like that, we can determine whether a target call
            // is guarded by a given SDK check: that will be the case if we cannot reach
            // the target call in the call graph

            ApiCheckGraph graph = new ApiCheckGraph(requiredApi);
            ControlFlowGraph.create(graph, classNode, method);

            // Note: To debug unit tests, you may want to for example do
            //   ControlFlowGraph.Node callNode = graph.getNode(call);
            //   Set<ControlFlowGraph.Node> highlight = Sets.newHashSet(callNode);
            //   Files.write(graph.toDot(highlight), new File("/tmp/graph.gv"), Charsets.UTF_8);
            // This will generate a graphviz file you can visualize with the "dot" utility
            AbstractInsnNode first = method.instructions.get(0);
            return !graph.isConnected(first, call);
        } catch (AnalyzerException e) {
            context.log(e, null);
        }

        return false;
    }

    private static boolean containsSimpleSdkCheck(@NonNull MethodNode method) {
        // Look for a compiled version of "if (Build.VERSION.SDK_INT op N) {"
        InsnList nodes = method.instructions;
        for (int i = 0, n = nodes.size(); i < n; i++) {
            AbstractInsnNode instruction = nodes.get(i);
            if (isSdkVersionLookup(instruction)) {
                AbstractInsnNode bipush = getNextInstruction(instruction);
                if (bipush != null && bipush.getOpcode() == Opcodes.BIPUSH) {
                    AbstractInsnNode ifNode = getNextInstruction(bipush);
                    if (ifNode != null && ifNode.getType() == AbstractInsnNode.JUMP_INSN) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isSdkVersionLookup(@NonNull AbstractInsnNode instruction) {
        if (instruction.getOpcode() == Opcodes.GETSTATIC) {
            FieldInsnNode fieldNode = (FieldInsnNode) instruction;
            return (SDK_INT.equals(fieldNode.name)
                    && ANDROID_OS_BUILD_VERSION.equals(fieldNode.owner));
        }
        return false;
    }

    public static boolean isPrecededByVersionCheckExit(PsiElement element, int api) {
        PsiElement current = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
        if (current != null) {
            PsiElement prev = getPreviousStatement(current);
            if (prev == null) {
                //noinspection unchecked
                current = PsiTreeUtil.getParentOfType(current, PsiStatement.class, true,
                        PsiMethod.class, PsiClass.class);
            } else {
                current = prev;
            }
        }
        while (current != null) {
            if (current instanceof PsiIfStatement) {
                PsiIfStatement ifStatement = (PsiIfStatement)current;
                PsiStatement thenBranch = ifStatement.getThenBranch();
                PsiStatement elseBranch = ifStatement.getElseBranch();
                PsiExpression condition = ifStatement.getCondition();
                if (condition != null) {
                    if (thenBranch != null) {
                        Boolean ok = isVersionCheckConditional(api, condition, true, thenBranch,
                                null);
                        //noinspection VariableNotUsedInsideIf
                        if (ok != null) {
                            // See if the body does an immediate return
                            if (isUnconditionalReturn(thenBranch)) {
                                return true;
                            }
                        }
                    }
                    if (elseBranch != null) {
                        Boolean ok = isVersionCheckConditional(api, condition, false, elseBranch,
                                null);

                        //noinspection VariableNotUsedInsideIf
                        if (ok != null) {
                            if (isUnconditionalReturn(elseBranch)) {
                                return true;
                            }
                        }
                    }
                }
            }
            PsiElement prev = getPreviousStatement(current);
            if (prev == null) {
                //noinspection unchecked
                current = PsiTreeUtil.getParentOfType(current, PsiStatement.class, true,
                        PsiMethod.class, PsiClass.class);
                if (current == null) {
                    return false;
                }
            } else {
                current = prev;
            }
        }

        return false;
    }

    private static boolean isUnconditionalReturn(PsiStatement statement) {
        if (statement instanceof PsiBlockStatement) {
            PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
            PsiCodeBlock block = blockStatement.getCodeBlock();
            PsiStatement[] statements = block.getStatements();
            if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
                return true;
            }
        }
        return statement instanceof PsiReturnStatement;
    }

    @Nullable
    public static PsiStatement getPreviousStatement(PsiElement element) {
        final PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(element,
                PsiWhiteSpace.class, PsiComment.class);
        return prevStatement instanceof PsiStatement ? (PsiStatement)prevStatement : null;
    }

    public static boolean isWithinVersionCheckConditional(@NonNull PsiElement element, int api) {
        PsiElement current = skipParentheses(element.getParent());
        PsiElement prev = element;
        while (current != null) {
            if (current instanceof PsiIfStatement) {
                PsiIfStatement ifStatement = (PsiIfStatement) current;
                PsiExpression condition = ifStatement.getCondition();
                if (prev != condition && condition != null) {
                    boolean fromThen = prev == ifStatement.getThenBranch();
                    Boolean ok = isVersionCheckConditional(api, condition, fromThen, prev, null);
                    if (ok != null) {
                        return ok;
                    }
                }
            } else if (current instanceof PsiConditionalExpression) {
                PsiConditionalExpression ifStatement = (PsiConditionalExpression)current;
                PsiExpression condition = ifStatement.getCondition();
                if (prev != condition) {
                    boolean fromThen = prev == ifStatement.getThenExpression();
                    Boolean ok = isVersionCheckConditional(api, condition, fromThen, prev, null);
                    if (ok != null) {
                        return ok;
                    }
                }
            } else if (current instanceof PsiPolyadicExpression &&
                    (isAndedWithConditional(current, api, prev) ||
                            isOredWithConditional(current, api, prev))) {
                return true;
            } else if (current instanceof PsiMethod || current instanceof PsiFile) {
                return false;
            }
            prev = current;
            current = skipParentheses(current.getParent());
        }

        return false;
    }

    @Nullable
    private static Boolean isVersionCheckConditional(int api,
            @NonNull PsiElement element, boolean and, @Nullable PsiElement prev,
            @Nullable ApiLevelLookup apiLookup) {
        if (element instanceof PsiPolyadicExpression) {
            if (element instanceof PsiBinaryExpression) {
                Boolean ok = isVersionCheckConditional(api, and, (PsiBinaryExpression) element,
                        apiLookup);
                if (ok != null) {
                    return ok;
                }
            }
            PsiPolyadicExpression expression = (PsiPolyadicExpression) element;
            IElementType tokenType = expression.getOperationTokenType();
            if (and && tokenType == JavaTokenType.ANDAND) {
                if (isAndedWithConditional(element, api, prev)) {
                    return true;
                }

            }  else if (!and && tokenType == JavaTokenType.OROR) {
                if (isOredWithConditional(element, api, prev)) {
                    return true;
                }
            }
        } else if (element instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) element;
            PsiMethod method = call.resolveMethod();
            if (method == null) {
                return null;
            }
            String name = method.getName();
            if (name.startsWith("isAtLeast")) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && "android.support.v4.os.BuildCompat".equals(
                        containingClass.getQualifiedName())) {
                    if (name.equals("isAtLeastN")) {
                        return api <= 24;
                    } else if (name.equals("isAtLeastNMR1")) {
                        return api <= 25;
                    }
                }
            }
            PsiCodeBlock body = method.getBody();
            if (body == null) {
                return null;
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length != 1) {
                return null;
            }
            PsiStatement statement = statements[0];
            if (!(statement instanceof PsiReturnStatement)) {
                return null;
            }
            PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return null;
            }
            PsiExpression[] expressions = call.getArgumentList().getExpressions();
            if (expressions.length == 0) {
                Boolean ok = isVersionCheckConditional(api, returnValue, and,
                        null, null);
                if (ok != null) {
                    return ok;
                }
            }

            if (expressions.length == 1) {
                // See if we're passing in a value
                ApiLevelLookup lookup = arg -> {
                    if (arg instanceof PsiReferenceExpression) {
                        PsiElement resolved = ((PsiReferenceExpression) arg).resolve();
                        if (resolved instanceof PsiParameter) {
                            PsiParameter parameter = (PsiParameter) resolved;
                            PsiParameterList parameterList = PsiTreeUtil.getParentOfType(resolved,
                                            PsiParameterList.class);
                            if (parameterList != null) {
                                int index = parameterList.getParameterIndex(parameter);
                                if (index != -1 && index < expressions.length) {
                                    return getApiLevel(expressions[index], null);
                                }
                            }
                        }
                    }
                    return -1;
                };
                Boolean ok = isVersionCheckConditional(api, returnValue, and, null, lookup);
                if (ok != null) {
                    return ok;
                }

            }
        } else if (element instanceof PsiReferenceExpression) {
            // Constant expression for an SDK version check?
            PsiReferenceExpression refExpression = (PsiReferenceExpression) element;
            PsiElement resolved = refExpression.resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiModifierList modifierList = field.getModifierList();
                if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                    PsiExpression initializer = field.getInitializer();
                    if (initializer != null) {
                        Boolean ok = isVersionCheckConditional(api, initializer, and, null, null);
                        if (ok != null) {
                            return ok;
                        }
                    }
                }

            }
        } else if (element instanceof PsiPrefixExpression) {
            PsiPrefixExpression prefixExpression = (PsiPrefixExpression) element;
            if (prefixExpression.getOperationTokenType() == JavaTokenType.EXCL) {
                PsiExpression operand = prefixExpression.getOperand();
                if (operand != null) {
                    Boolean ok = isVersionCheckConditional(api, operand, !and, null, null);
                    if (ok != null) {
                        return ok;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSdkInt(@NonNull PsiElement element) {
        if (element instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression) element;
            if (SDK_INT.equals(ref.getReferenceName())) {
                return true;
            }
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiVariable) {
                PsiExpression initializer = ((PsiVariable) resolved).getInitializer();
                if (initializer != null) {
                    return isSdkInt(initializer);
                }
            }
        } else if (element instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
            if ("getBuildSdkInt".equals(callExpression.getMethodExpression().getReferenceName())) {
                return true;
            } // else look inside the body?
        }

        return false;
    }

    @Nullable
    private static Boolean isVersionCheckConditional(int api,
            boolean fromThen,
            @NonNull PsiBinaryExpression binary,
            @Nullable ApiLevelLookup apiLevelLookup) {
        IElementType tokenType = binary.getOperationTokenType();
        if (tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE ||
                tokenType == JavaTokenType.LE || tokenType == JavaTokenType.LT ||
                tokenType == JavaTokenType.EQEQ) {
            PsiExpression left = binary.getLOperand();
            int level;
            PsiExpression right;
            if (!isSdkInt(left)) {
                right = binary.getROperand();
                if (right != null && isSdkInt(right)) {
                    fromThen = !fromThen;
                    level = getApiLevel(left, apiLevelLookup);
                } else {
                    return null;
                }
            } else {
                right = binary.getROperand();
                level = getApiLevel(right, apiLevelLookup);
            }
            if (level != -1) {
                if (tokenType == JavaTokenType.GE) {
                    // if (SDK_INT >= ICE_CREAM_SANDWICH) { <call> } else { ... }
                    return level >= api && fromThen;
                }
                else if (tokenType == JavaTokenType.GT) {
                    // if (SDK_INT > ICE_CREAM_SANDWICH) { <call> } else { ... }
                    return level >= api - 1 && fromThen;
                }
                else if (tokenType == JavaTokenType.LE) {
                    // if (SDK_INT <= ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return level >= api - 1 && !fromThen;
                }
                else if (tokenType == JavaTokenType.LT) {
                    // if (SDK_INT < ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return level >= api && !fromThen;
                }
                else if (tokenType == JavaTokenType.EQEQ) {
                    // if (SDK_INT == ICE_CREAM_SANDWICH) { <call> } else {  }
                    return level >= api && fromThen;
                } else {
                    assert false : tokenType;
                }
            }
        }
        return null;
    }

    private static int getApiLevel(
            @Nullable PsiExpression element,
            @Nullable ApiLevelLookup apiLevelLookup) {
        int level = -1;
        if (element instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref2 = (PsiReferenceExpression)element;
            String codeName = ref2.getReferenceName();
            if (codeName != null) {
                level = SdkVersionInfo.getApiByBuildCode(codeName, false);
            }
        } else if (element instanceof PsiLiteralExpression) {
            PsiLiteralExpression lit = (PsiLiteralExpression)element;
            Object value = lit.getValue();
            if (value instanceof Integer) {
                level = (Integer) value;
            }
        }
        if (level == -1 && apiLevelLookup != null && element != null) {
            level = apiLevelLookup.getApiLevel(element);
        }
        return level;
    }

    private static boolean isOredWithConditional(PsiElement element, int api,
            @Nullable PsiElement before) {
        if (element instanceof PsiBinaryExpression) {
            PsiBinaryExpression inner = (PsiBinaryExpression) element;
            if (inner.getOperationTokenType() == JavaTokenType.OROR) {
                PsiExpression left = inner.getLOperand();

                if (before != left) {
                    Boolean ok = isVersionCheckConditional(api, left, false, null, null);
                    if (ok != null) {
                        return ok;
                    }
                    PsiExpression right = inner.getROperand();
                    if (right != null) {
                        ok = isVersionCheckConditional(api, right, false, null, null);
                        if (ok != null) {
                            return ok;
                        }
                    }
                }
            }
            Boolean value = isVersionCheckConditional(api, false, inner, null);
            return value != null && value;
        } else if (element instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression ppe = (PsiPolyadicExpression) element;
            if (ppe.getOperationTokenType() == JavaTokenType.OROR) {
                for (PsiExpression operand : ppe.getOperands()) {
                    if (operand == before) {
                        break;
                    } else if (isOredWithConditional(operand, api, before)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isAndedWithConditional(PsiElement element, int api,
            @Nullable PsiElement before) {
        if (element instanceof PsiBinaryExpression) {
            PsiBinaryExpression inner = (PsiBinaryExpression) element;
            if (inner.getOperationTokenType() == JavaTokenType.ANDAND) {
                PsiExpression left = inner.getLOperand();
                if (before != left) {
                    Boolean ok = isVersionCheckConditional(api, left, true, null, null);
                    if (ok != null) {
                        return ok;
                    }
                    PsiExpression right = inner.getROperand();
                    if (right != null) {
                        ok = isVersionCheckConditional(api, right, true, null, null);
                        if (ok != null) {
                            return ok;
                        }
                    }
                }
            }

            Boolean value = isVersionCheckConditional(api, true, inner, null);
            return value != null && value;
        } else if (element instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression ppe = (PsiPolyadicExpression) element;
            if (ppe.getOperationTokenType() == JavaTokenType.ANDAND) {
                for (PsiExpression operand : ppe.getOperands()) {
                    if (operand == before) {
                        break;
                    } else if (isAndedWithConditional(operand, api, before)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // TODO: Merge with the other isVersionCheckConditional
    @Nullable
    public static Boolean isVersionCheckConditional(int api,
            @NonNull PsiBinaryExpression binary) {
        IElementType tokenType = binary.getOperationTokenType();
        if (tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE ||
                tokenType == JavaTokenType.LE || tokenType == JavaTokenType.LT ||
                tokenType == JavaTokenType.EQEQ) {
            PsiExpression left = binary.getLOperand();
            if (left instanceof PsiReferenceExpression) {
                PsiReferenceExpression ref = (PsiReferenceExpression) left;
                if (SDK_INT.equals(ref.getReferenceName())) {
                    PsiExpression right = binary.getROperand();
                    int level = -1;
                    if (right instanceof PsiReferenceExpression) {
                        PsiReferenceExpression ref2 = (PsiReferenceExpression) right;
                        String codeName = ref2.getReferenceName();
                        if (codeName == null) {
                            return false;
                        }
                        level = SdkVersionInfo.getApiByBuildCode(codeName, true);
                    } else if (right instanceof PsiLiteralExpression) {
                        PsiLiteralExpression lit = (PsiLiteralExpression) right;
                        Object value = lit.getValue();
                        if (value instanceof Integer) {
                            level = (Integer) value;
                        }
                    }
                    if (level != -1) {
                        if (tokenType == JavaTokenType.GE && level < api) {
                            // SDK_INT >= ICE_CREAM_SANDWICH
                            return true;
                        } else if (tokenType == JavaTokenType.GT && level <= api - 1) {
                            // SDK_INT > ICE_CREAM_SANDWICH
                            return true;
                        } else if (tokenType == JavaTokenType.LE && level < api) {
                            return false;
                        } else if (tokenType == JavaTokenType.LT && level <= api) {
                            // SDK_INT < ICE_CREAM_SANDWICH
                            return false;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Control flow graph which skips control flow edges that check
     * a given SDK_VERSION requirement that is not met by a given call
     */
    private static class ApiCheckGraph extends ControlFlowGraph {
        private final int mRequiredApi;

        public ApiCheckGraph(int requiredApi) {
            mRequiredApi = requiredApi;
        }

        @Override
        protected void add(@NonNull AbstractInsnNode from, @NonNull AbstractInsnNode to) {
            if (from.getType() == AbstractInsnNode.JUMP_INSN &&
                    from.getPrevious() != null &&
                    from.getPrevious().getType() == AbstractInsnNode.INT_INSN) {
                IntInsnNode intNode = (IntInsnNode) from.getPrevious();
                if (intNode.getPrevious() != null && isSdkVersionLookup(intNode.getPrevious())) {
                    JumpInsnNode jumpNode = (JumpInsnNode) from;
                    int api = intNode.operand;
                    boolean isJumpEdge = to == jumpNode.label;
                    boolean includeEdge;
                    switch (from.getOpcode()) {
                        case Opcodes.IF_ICMPNE:
                            includeEdge = api < mRequiredApi || isJumpEdge;
                            break;
                        case Opcodes.IF_ICMPLE:
                            includeEdge = api < mRequiredApi - 1 || isJumpEdge;
                            break;
                        case Opcodes.IF_ICMPLT:
                            includeEdge = api < mRequiredApi || isJumpEdge;
                            break;

                        case Opcodes.IF_ICMPGE:
                            includeEdge = api < mRequiredApi || !isJumpEdge;
                            break;
                        case Opcodes.IF_ICMPGT:
                            includeEdge = api < mRequiredApi - 1 || !isJumpEdge;
                            break;
                        default:
                            // unexpected comparison for int API level
                            includeEdge = true;
                    }
                    if (!includeEdge) {
                        return;
                    }
                }
            }

            super.add(from, to);
        }
    }
}
