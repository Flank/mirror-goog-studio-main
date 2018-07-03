/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.api.dsl.sealing

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import java.util.SortedMap
import java.util.SortedSet
import javax.inject.Inject

/**
 * A [NamedDomainObjectContainer] that can be sealed to prevent further updates.
 *
 * all methods returning sub collections, or iterators will return sealable versions
 * of this classes. Sealing the main collection will seal all the sub-items.
 *
 * @param InterfaceT the base interface exposed by the sealable container
 * @param ImplementationT the actual type that the wrapped container contains
 *
 * @see SealableObject
 */
open class SealableNamedDomainObjectContainer<InterfaceT, ImplementationT: InterfaceT>
    @Inject constructor(
            private val container: NamedDomainObjectContainer<ImplementationT>,
            private val implClass: Class<ImplementationT>,
            dslScope: DslScope)
    : NestedSealable(dslScope), NamedDomainObjectContainer<InterfaceT> {

    override fun seal() {
        super.seal()
        container.forEach {
            if (it is Sealable) {
                it.seal()
            }
        }
    }

    // wrapper with checkSeal

    override fun create(name: String): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun create(name: String, closure: Closure<*>): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, closure)
    }

    override fun create(name: String, action: Action<in InterfaceT>): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, action)
    }

    override fun addAll(elements: Collection<InterfaceT>): Boolean {
        if (checkSeal()) {
            val recastedElements = elements.asSequence()
                    .filter(implClass::isInstance)
                    .map(implClass::cast)
                    .toSet()

            if (recastedElements.size != elements.size) {
                val wrongTypeElements = elements.filter { !implClass.isInstance(it) }

                dslScope.issueReporter.reportError(EvalIssueReporter.Type.GENERIC,
                    EvalIssueException("Expected type ${implClass.name} for items: $wrongTypeElements"))
                return false
            }

            return container.addAll(recastedElements)
        }

        return false
    }

    override fun clear() {
        if (checkSeal()) {
            container.clear()
        }
    }

    override fun maybeCreate(name: String): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun remove(element: InterfaceT): Boolean {
        if (checkSeal()) {
            return container.remove(element)
        }

        return false
    }

    override fun removeAll(elements: Collection<InterfaceT>): Boolean {
        if (checkSeal()) {
            return container.removeAll(elements)
        }

        return false
    }

    override fun add(element: InterfaceT): Boolean {
        if (checkSeal()) {
            return if (implClass.isInstance(element)) {
                container.add(implClass.cast(element))
            } else {
                dslScope.issueReporter.reportError(EvalIssueReporter.Type.GENERIC,
                    EvalIssueException("Expected type ${implClass.name} for item: $element"))
                false
            }
        }

        return false
    }

    override fun retainAll(elements: Collection<InterfaceT>): Boolean {
        if (checkSeal()) {
            return container.retainAll(elements)
        }

        return false
    }

    override fun iterator(): MutableIterator<InterfaceT> {
        return handleSealableSubItem(
                SealableMutableIterator(container.iterator(), dslScope))
    }


    // basic wrappers

    override val size: Int
        get() = container.size

    override fun whenObjectRemoved(closure: Closure<*>) {
        container.whenObjectRemoved(closure)
    }

    override fun whenObjectRemoved(action: Action<in InterfaceT>): Action<in InterfaceT> {
        container.whenObjectRemoved(action)
        return action
    }

    override fun whenObjectAdded(closure: Closure<*>) {
        container.whenObjectAdded(closure)
    }

    override fun whenObjectAdded(action: Action<in InterfaceT>): Action<in InterfaceT> {
        container.whenObjectAdded(action)
        return action
    }

    override fun getByName(name: String): InterfaceT = container.getByName(name)

    override fun findByName(name: String): InterfaceT? = container.findByName(name)

    override fun getRules(): MutableList<Rule> = container.rules

    override fun addRule(description: String, action: Closure<*>): Rule = container.addRule(description, action)

    override fun addRule(description: String, action: Action<String>): Rule = container.addRule(description, action)

    override fun addRule(rule: Rule): Rule = container.addRule(rule)

    override fun configure(action: Closure<*>): NamedDomainObjectContainer<InterfaceT> {
        container.configure(action)
        return this
    }

    override fun <S : InterfaceT > withType(type: Class<S>, closure: Closure<*>): DomainObjectCollection<S> {
        if (!implClass.isAssignableFrom(type)) {
            // the sub class of the interface is not a sub class of the internal implementation.
            // need to break
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Type ${type.canonicalName} cannot be used with withType() because it does not extend internal type ${implClass.name}"))

            // still need to return something for the case where sync happens so we return a,
            // container with all the items.
            // FIXME We can't use the closure!
            // it is not safe as it's a closure to configure a type that is not valid for the
            // internal implementation. Maybe we should use an empty list to ensure the closure
            // is not called?
            @Suppress("UNCHECKED_CAST")
            return container.withType(implClass, closure) as DomainObjectCollection<S>
        }

        // we know this is correct because we checked above that the requested type does extend
        // the internal implementation
        @Suppress("UNCHECKED_CAST")
        return container.withType(type as Class<ImplementationT>, closure) as DomainObjectCollection<S>
    }

    override fun <S : InterfaceT> withType(type: Class<S>): NamedDomainObjectSet<S> {
        if (!implClass.isAssignableFrom(type)) {
            // the sub class of the interface is not a sub class of the internal implementation.
            // need to break
            dslScope.issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Type ${type.canonicalName} cannot be used with withType() because it does not extend internal type ${implClass.name}"))

            // still need to return something for the case where sync happens so we return a,
            // container with all the items.
            @Suppress("UNCHECKED_CAST")
            return container.withType(implClass) as NamedDomainObjectSet<S>
        }

        // we know this is correct because we checked above that the requested type does extend
        // the internal implementation
        @Suppress("UNCHECKED_CAST")
        return container.withType(type as Class<ImplementationT>) as NamedDomainObjectSet<S>
    }

    override fun <S : InterfaceT> withType(type: Class<S>, action: Action<in S>): DomainObjectCollection<S> {
        if (!implClass.isAssignableFrom(type)) {
            // the sub class of the interface is not a sub class of the internal implementation.
            // need to break
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Type ${type.canonicalName} cannot be used with withType() because it does not extend internal type ${implClass.name}"))

            // still need to return something for the case where sync happens so we return a,
            // container with all the items.
            // FIXME We can't use the action!
            // it is not safe as it's an action to configure a type that is not valid for the
            // internal implementation. Maybe we should use an empty list to ensure the action
            // is not called?
            @Suppress("UNCHECKED_CAST")
            return container.withType(implClass, action as Action<ImplementationT>) as DomainObjectCollection<S>
        }

        // we know this is correct because we checked above that the requested type does extend
        // the internal implementation
        @Suppress("UNCHECKED_CAST")
        return container.withType(type as Class<ImplementationT>, action as Action<ImplementationT>) as DomainObjectCollection<S>
    }

    override fun matching(spec: Spec<in InterfaceT>): NamedDomainObjectSet<InterfaceT> {
        @Suppress("UNCHECKED_CAST")
        return container.matching(spec) as NamedDomainObjectSet<InterfaceT>
    }

    override fun matching(spec: Closure<*>): NamedDomainObjectSet<InterfaceT> {
        @Suppress("UNCHECKED_CAST")
        return container.matching(spec) as NamedDomainObjectSet<InterfaceT>
    }

    override fun getNamer(): Namer<InterfaceT> {
        @Suppress("UNCHECKED_CAST")
        return container.namer as Namer<InterfaceT>
    }

    // TODO


    override fun getAsMap(): SortedMap<String, InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(p0: Action<in InterfaceT>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(p0: Closure<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAt(p0: String?): InterfaceT {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNames(): SortedSet<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAll(elements: Collection<InterfaceT>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByName(p0: String?, p1: Closure<*>?): InterfaceT {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByName(p0: String?, p1: Action<in InterfaceT>?): InterfaceT {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(element: InterfaceT): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findAll(p0: Closure<*>?): MutableSet<InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun configureEach(p0: Action<in InterfaceT>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLater(p0: Provider<out InterfaceT>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}