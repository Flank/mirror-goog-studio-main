/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.ATTR_VALUE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.List;

import lombok.ast.Catch;
import lombok.ast.For;
import lombok.ast.Identifier;
import lombok.ast.If;
import lombok.ast.Node;
import lombok.ast.Return;
import lombok.ast.StrictListAccessor;
import lombok.ast.Switch;
import lombok.ast.Throw;
import lombok.ast.TypeReference;
import lombok.ast.TypeReferencePart;
import lombok.ast.While;

/**
 * A wrapper for a Java parser. This allows tools integrating lint to map directly
 * to builtin services, such as already-parsed data structures in Java editors.
 * <p/>
 * <b>NOTE: This is not public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public abstract class JavaParser {
    public static final String TYPE_OBJECT = "java.lang.Object";        //$NON-NLS-1$
    public static final String TYPE_STRING = "java.lang.String";        //$NON-NLS-1$
    public static final String TYPE_INT = "int";                        //$NON-NLS-1$
    public static final String TYPE_LONG = "long";                      //$NON-NLS-1$
    public static final String TYPE_CHAR = "char";                      //$NON-NLS-1$
    public static final String TYPE_FLOAT = "float";                    //$NON-NLS-1$
    public static final String TYPE_DOUBLE = "double";                  //$NON-NLS-1$
    public static final String TYPE_BOOLEAN = "boolean";                //$NON-NLS-1$
    public static final String TYPE_SHORT = "short";                    //$NON-NLS-1$
    public static final String TYPE_BYTE = "byte";                      //$NON-NLS-1$
    public static final String TYPE_NULL = "null";                      //$NON-NLS-1$

    /**
     * Prepare to parse the given contexts. This method will be called before
     * a series of {@link #parseJava(JavaContext)} calls, which allows some
     * parsers to do up front global computation in case they want to more
     * efficiently process multiple files at the same time. This allows a single
     * type-attribution pass for example, which is a lot more efficient than
     * performing global type analysis over and over again for each individual
     * file
     *
     * @param contexts a list of contexts to be parsed
     */
    public abstract void prepareJavaParse(@NonNull List<JavaContext> contexts);

    /**
     * Parse the file pointed to by the given context.
     *
     * @param context the context pointing to the file to be parsed, typically
     *            via {@link Context#getContents()} but the file handle (
     *            {@link Context#file} can also be used to map to an existing
     *            editor buffer in the surrounding tool, etc)
     * @return the compilation unit node for the file
     */
    @Nullable
    public abstract Node parseJava(@NonNull JavaContext context);

    /**
     * Returns a {@link Location} for the given node
     *
     * @param context information about the file being parsed
     * @param node    the node to create a location for
     * @return a location for the given node
     */
    @NonNull
    public abstract Location getLocation(@NonNull JavaContext context, @NonNull Node node);

    /**
     * Returns a {@link Location} for the given node range (from the starting offset of the first
     * node to the ending offset of the second node).
     *
     * @param from      the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param to        the AST node to get a ending location from
     * @param toDelta   Offset delta to apply to the ending offset
     * @return a location for the given node
     */
    @NonNull
    public abstract Location getRangeLocation(
            @NonNull JavaContext context,
            @NonNull Node from,
            int fromDelta,
            @NonNull Node to,
            int toDelta);

    /**
     * Returns a {@link Location} for the given node. This attempts to pick a shorter
     * location range than the entire node; for a class or method for example, it picks
     * the name node (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    @NonNull
    public Location getNameLocation(@NonNull JavaContext context, @NonNull Node node) {
        Node nameNode = JavaContext.findNameNode(node);
        if (nameNode != null) {
            node = nameNode;
        } else {
            if (node instanceof Switch
                    || node instanceof For
                    || node instanceof If
                    || node instanceof While
                    || node instanceof Throw
                    || node instanceof Return) {
                // Lint doesn't want to highlight the entire statement/block associated
                // with this node, it wants to just highlight the keyword.
                Location location = getLocation(context, node);
                Position start = location.getStart();
                if (start != null) {
                    // The Lombok classes happen to have the same length as the target keyword
                    int length = node.getClass().getSimpleName().length();
                    return Location.create(location.getFile(), start,
                            new DefaultPosition(start.getLine(), start.getColumn() + length,
                                    start.getOffset() + length));
                }
            }
        }

        return getLocation(context, node);
    }

    /**
     * Creates a light-weight handle to a location for the given node. It can be
     * turned into a full fledged location by
     * {@link com.android.tools.lint.detector.api.Location.Handle#resolve()}.
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location handle
     *            for
     * @return a location handle
     */
    @NonNull
    public abstract Location.Handle createLocationHandle(@NonNull JavaContext context,
            @NonNull Node node);

    /**
     * Dispose any data structures held for the given context.
     * @param context information about the file previously parsed
     * @param compilationUnit the compilation unit being disposed
     */
    public void dispose(@NonNull JavaContext context, @NonNull Node compilationUnit) {
    }

    /**
     * Dispose any remaining data structures held for all contexts.
     * Typically frees up any resources allocated by
     * {@link #prepareJavaParse(List)}
     */
    public void dispose() {
    }

    /**
     * Resolves the given expression node: computes the declaration for the given symbol
     *
     * @param context information about the file being parsed
     * @param node the node to resolve
     * @return a node representing the resolved fully type: class/interface/annotation,
     *          field, method or variable
     */
    @Nullable
    public abstract ResolvedNode resolve(@NonNull JavaContext context, @NonNull Node node);

    /**
     * Finds the given type, if possible (which should be reachable from the compilation
     * patch of the given node.
     *
     * @param context information about the file being parsed
     * @param fullyQualifiedName the fully qualified name of the class to look up
     * @return the class, or null if not found
     */
    @Nullable
    public ResolvedClass findClass(
            @NonNull JavaContext context,
            @NonNull String fullyQualifiedName) {
        return null;
    }

    /**
     * Returns the set of exception types handled by the given catch block.
     * <p>
     * This is a workaround for the fact that the Lombok AST API (and implementation)
     * doesn't support multi-catch statements.
     */
    public List<TypeDescriptor> getCatchTypes(@NonNull JavaContext context,
            @NonNull Catch catchBlock) {
        TypeReference typeReference = catchBlock.astExceptionDeclaration().astTypeReference();
        return Collections.<TypeDescriptor>singletonList(new DefaultTypeDescriptor(
                typeReference.getTypeName()));
    }

    /**
     * Gets the type of the given node
     *
     * @param context information about the file being parsed
     * @param node the node to look up the type for
     * @return the type of the node, if known
     */
    @Nullable
    public abstract TypeDescriptor getType(@NonNull JavaContext context, @NonNull Node node);

    /** A description of a type, such as a primitive int or the android.app.Activity class */
    public abstract static class TypeDescriptor {
        /**
         * Returns the fully qualified name of the type, such as "int" or "android.app.Activity"
         * */
        @NonNull public abstract String getName();

        /** Returns the simple name of this class */
        @NonNull
        public String getSimpleName() {
            // This doesn't handle inner classes properly, so subclasses with more
            // accurate type information will override to handle it correctly.
            String name = getName();
            int index = name.lastIndexOf('.');
            if (index != -1) {
                return name.substring(index + 1);
            }
            return name;
        }

        /**
         * Returns the full signature of the type, which is normally the same as {@link #getName()}
         * but for arrays can include []'s, for generic methods can include generics parameters
         * etc
         */
        @NonNull public abstract String getSignature();

        /**
         * Computes the internal class name of the given fully qualified class name.
         * For example, it converts foo.bar.Foo.Bar into foo/bar/Foo$Bar.
         * This should only be called for class types, not primitives.
         *
         * @return the internal class name
         */
        @NonNull public String getInternalName() {
            return ClassContext.getInternalName(getName());
        }

        public abstract boolean matchesName(@NonNull String name);

        /**
         * Returns true if the given TypeDescriptor represents an array
         * @return true if this type represents an array
         */
        public abstract boolean isArray();

        /**
         * Returns true if the given TypeDescriptor represents a primitive
         * @return true if this type represents a primitive
         */
        public abstract boolean isPrimitive();

        public abstract boolean matchesSignature(@NonNull String signature);

        @NonNull
        public TypeReference getNode() {
            TypeReference typeReference = new TypeReference();
            StrictListAccessor<TypeReferencePart, TypeReference> parts = typeReference.astParts();
            for (String part : Splitter.on('.').split(getName())) {
                Identifier identifier = Identifier.of(part);
                parts.addToEnd(new TypeReferencePart().astIdentifier(identifier));
            }

            return typeReference;
        }

        /** If the type is not primitive, returns the class of the type if known */
        @Nullable
        public abstract ResolvedClass getTypeClass();

        @Override
        public abstract boolean equals(Object o);

        @Override
        public String toString() {
            return getName();
        }
    }

    /** Convenience implementation of {@link TypeDescriptor} */
    public static class DefaultTypeDescriptor extends TypeDescriptor {

        private String mName;

        public DefaultTypeDescriptor(String name) {
            mName = name;
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
        }

        @NonNull
        @Override
        public String getSignature() {
            return getName();
        }

        @Override
        public boolean matchesName(@NonNull String name) {
            return mName.equals(name);
        }

        @Override
        public boolean isArray() {
            return mName.endsWith("[]");
        }

        @Override
        public boolean isPrimitive() {
            return mName.indexOf('.') != -1;
        }

        @Override
        public boolean matchesSignature(@NonNull String signature) {
            return matchesName(signature);
        }

        @Override
        public String toString() {
            return getSignature();
        }

        @Override
        @Nullable
        public ResolvedClass getTypeClass() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DefaultTypeDescriptor that = (DefaultTypeDescriptor) o;

            return !(mName != null ? !mName.equals(that.mName) : that.mName != null);

        }

        @Override
        public int hashCode() {
            return mName != null ? mName.hashCode() : 0;
        }
    }

    /** A resolved declaration from an AST Node reference */
    public abstract static class ResolvedNode {
        @NonNull
        public abstract String getName();

        /** Returns the signature of the resolved node */
        public abstract String getSignature();

        public abstract int getModifiers();

        @Override
        public String toString() {
            return getSignature();
        }

        /** Returns any annotations defined on this node */
        @NonNull
        public abstract Iterable<ResolvedAnnotation> getAnnotations();

        /**
         * Searches for the annotation of the given type on this node
         *
         * @param type the fully qualified name of the annotation to check
         * @return the annotation, or null if not found
         */
        @Nullable
        public ResolvedAnnotation getAnnotation(@NonNull String type) {
            for (ResolvedAnnotation annotation : getAnnotations()) {
                if (annotation.getType().matchesSignature(type)) {
                    return annotation;
                }
            }

            return null;
        }

        /**
         * Returns true if this element is in the given package (or optionally, in one of its sub
         * packages)
         *
         * @param pkg                the package name
         * @param includeSubPackages whether to include subpackages
         * @return true if the element is in the given package
         */
        public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
            return getSignature().startsWith(pkg);
        }

        /**
         * Attempts to find the corresponding AST node, if possible. This won't work if for example
         * the resolved node is from a binary (such as a compiled class in a .jar) or if the
         * underlying parser doesn't support it.
         * <p>
         * Note that looking up the AST node can result in different instances for each lookup.
         *
         * @return an AST node, if possible.
         */
        @Nullable
        public Node findAstNode() {
            return null;
        }
    }

    /** A resolved class declaration (class, interface, enumeration or annotation) */
    public abstract static class ResolvedClass extends ResolvedNode {
        /** Returns the fully qualified name of this class */
        @Override
        @NonNull
        public abstract String getName();

        /** Returns the simple name of this class */
        @NonNull
        public abstract String getSimpleName();

        /** Returns the package name of this class */
        @NonNull
        public String getPackageName() {
            String name = getName();
            String simpleName = getSimpleName();
            if (name.length() > simpleName.length() + 1) {
                return name.substring(0, name.length() - simpleName.length() - 1);
            }
            return name;
        }

        /** Returns whether this class' fully qualified name matches the given name */
        public abstract boolean matches(@NonNull String name);

        @Nullable
        public abstract ResolvedClass getSuperClass();

        @NonNull
        public abstract Iterable<ResolvedClass> getInterfaces();

        @Nullable
        public abstract ResolvedClass getContainingClass();

        public TypeDescriptor getType() {
            return new DefaultTypeDescriptor(getName());
        }

        /**
         * Determines whether this class extends the given name. If strict is true,
         * it will not consider C extends C true.
         * <p>
         * The target must be a class; to check whether this class extends an interface,
         * use {@link #isImplementing(String,boolean)} instead. If you're not sure, use
         * {@link #isInheritingFrom(String, boolean)}.
         *
         * @param name the fully qualified class name
         * @param strict if true, do not consider a class to be extending itself
         * @return true if this class extends the given class
         */
        public abstract boolean isSubclassOf(@NonNull String name, boolean strict);

        /**
         * Determines whether this is implementing the given interface.
         * <p>
         * The target must be an interface; to check whether this class extends a class,
         * use {@link #isSubclassOf(String, boolean)} instead. If you're not sure, use
         * {@link #isInheritingFrom(String, boolean)}.
         *
         * @param name the fully qualified interface name
         * @param strict if true, do not consider a class to be extending itself
         * @return true if this class implements the given interface
         */
        public abstract boolean isImplementing(@NonNull String name, boolean strict);

        /**
         * Determines whether this class extends or implements the class of the given name.
         * If strict is true, it will not consider C extends C true.
         * <p>
         * For performance reasons, if you know that the target is a class, consider using
         * {@link #isSubclassOf(String, boolean)} instead, and if the target is an interface,
         * consider using {@link #isImplementing(String,boolean)}.
         *
         * @param name the fully qualified class name
         * @param strict if true, do not consider a class to be inheriting from itself
         * @return true if this class extends or implements the given class
         */
        public abstract boolean isInheritingFrom(@NonNull String name, boolean strict);

        @NonNull
        public abstract Iterable<ResolvedMethod> getConstructors();

        /** Returns the methods defined in this class, and optionally any methods inherited from any superclasses as well */
        @NonNull
        public abstract Iterable<ResolvedMethod> getMethods(boolean includeInherited);

        /** Returns the methods of a given name defined in this class, and optionally any methods inherited from any superclasses as well */
        @NonNull
        public abstract Iterable<ResolvedMethod> getMethods(@NonNull String name, boolean includeInherited);

        /** Returns the fields defined in this class, and optionally any fields declared in any superclasses as well */
        @NonNull
        public abstract Iterable<ResolvedField> getFields(boolean includeInherited);

        /** Returns the named field defined in this class, or optionally inherited from a superclass */
        @Nullable
        public abstract ResolvedField getField(@NonNull String name, boolean includeInherited);

        /** Returns the package containing this class */
        @Nullable
        public abstract ResolvedPackage getPackage();

        @Override
        public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
            String packageName = getPackageName();

            //noinspection SimplifiableIfStatement
            if (pkg.equals(packageName)) {
                return true;
            }

            return includeSubPackages && packageName.length() > pkg.length() &&
                    packageName.charAt(pkg.length()) == '.' &&
                    packageName.startsWith(pkg);
        }
    }

    /** A method or constructor declaration */
    public abstract static class ResolvedMethod extends ResolvedNode {
        @Override
        @NonNull
        public abstract String getName();

        /** Returns whether this method name matches the given name */
        public abstract boolean matches(@NonNull String name);

        @NonNull
        public abstract ResolvedClass getContainingClass();

        public abstract int getArgumentCount();

        @NonNull
        public abstract TypeDescriptor getArgumentType(int index);

        /** Returns true if the parameter at the given index matches the given type signature */
        public boolean argumentMatchesType(int index, @NonNull String signature) {
            return getArgumentType(index).matchesSignature(signature);
        }

        @Nullable
        public abstract TypeDescriptor getReturnType();

        public boolean isConstructor() {
            return getReturnType() == null;
        }

        /** Returns any annotations defined on the given parameter of this method */
        @NonNull
        public abstract Iterable<ResolvedAnnotation> getParameterAnnotations(int index);

        /**
         * Searches for the annotation of the given type on the method
         *
         * @param type the fully qualified name of the annotation to check
         * @param parameterIndex the index of the parameter to look up
         * @return the annotation, or null if not found
         */
        @Nullable
        public ResolvedAnnotation getParameterAnnotation(@NonNull String type,
                int parameterIndex) {
            for (ResolvedAnnotation annotation : getParameterAnnotations(parameterIndex)) {
                if (annotation.getType().matchesSignature(type)) {
                    return annotation;
                }
            }

            return null;
        }

        /** Returns the super implementation of the given method, if any */
        @Nullable
        public ResolvedMethod getSuperMethod() {
            ResolvedClass cls = getContainingClass().getSuperClass();
            if (cls != null) {
                String methodName = getName();
                int argCount = getArgumentCount();
                for (ResolvedMethod method : cls.getMethods(methodName, true)) {
                    if (argCount != method.getArgumentCount()) {
                        continue;
                    }
                    boolean sameTypes = true;
                    for (int arg = 0; arg < argCount; arg++) {
                        if (!method.getArgumentType(arg).equals(getArgumentType(arg))) {
                            sameTypes = false;
                            break;
                        }
                    }
                    if (sameTypes) {
                        return method;
                    }
                }
            }

            return null;
        }

        @Override
        public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
            String packageName = getContainingClass().getPackageName();

            //noinspection SimplifiableIfStatement
            if (pkg.equals(packageName)) {
                return true;
            }

            return includeSubPackages && packageName.length() > pkg.length() &&
                    packageName.charAt(pkg.length()) == '.' &&
                    packageName.startsWith(pkg);
        }
    }

    /** A field declaration */
    public abstract static class ResolvedField extends ResolvedNode {
        @Override
        @NonNull
        public abstract String getName();

        /** Returns whether this field name matches the given name */
        public abstract boolean matches(@NonNull String name);

        @NonNull
        public abstract TypeDescriptor getType();

        @Nullable
        public abstract ResolvedClass getContainingClass();

        @Nullable
        public abstract Object getValue();

        @Nullable
        public String getContainingClassName() {
            ResolvedClass containingClass = getContainingClass();
            return containingClass != null ? containingClass.getName() : null;
        }

        @Override
        public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
            ResolvedClass containingClass = getContainingClass();
            if (containingClass == null) {
                return false;
            }

            String packageName = containingClass.getPackageName();

            //noinspection SimplifiableIfStatement
            if (pkg.equals(packageName)) {
                return true;
            }

            return includeSubPackages && packageName.length() > pkg.length() &&
                   packageName.charAt(pkg.length()) == '.' &&
                   packageName.startsWith(pkg);
        }
    }

    /**
     * An annotation <b>reference</b>. Note that this refers to a usage of an annotation,
     * not a declaraton of an annotation. You can call {@link #getClassType()} to
     * find the declaration for the annotation.
     */
    public abstract static class ResolvedAnnotation extends ResolvedNode {
        @Override
        @NonNull
        public abstract String getName();

        /** Returns whether this field name matches the given name */
        public abstract boolean matches(@NonNull String name);

        @NonNull
        public abstract TypeDescriptor getType();

        /** Returns the {@link ResolvedClass} which defines the annotation */
        @Nullable
        public abstract ResolvedClass getClassType();

        public static class Value {
            @NonNull public final String name;
            @Nullable public final Object value;

            public Value(@NonNull String name, @Nullable Object value) {
                this.name = name;
                this.value = value;
            }
        }

        @NonNull
        public abstract List<Value> getValues();

        @Nullable
        public Object getValue(@NonNull String name) {
            for (Value value : getValues()) {
                if (name.equals(value.name)) {
                    return value.value;
                }
            }
            return null;
        }

        @Nullable
        public Object getValue() {
            return getValue(ATTR_VALUE);
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            return Collections.emptyList();
        }
    }

    /** A package declaration */
    public abstract static class ResolvedPackage extends ResolvedNode {
        /** Returns the parent package of this package, if any. */
        @Nullable
        public abstract ResolvedPackage getParentPackage();

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            return Collections.emptyList();
        }
    }

    /** A local variable or parameter declaration */
    public abstract static class ResolvedVariable extends ResolvedNode {
        @Override
        @NonNull
        public abstract String getName();

        /** Returns whether this variable name matches the given name */
        public abstract boolean matches(@NonNull String name);

        @NonNull
        public abstract TypeDescriptor getType();
    }
}
