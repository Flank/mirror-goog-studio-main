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

import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.specs.Spec
import java.util.SortedMap
import java.util.SortedSet
import java.util.stream.Collectors

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
class SealableNamedDomainObjectContainer<InterfaceT, ImplementationT: InterfaceT>(
            private val container: NamedDomainObjectContainer<ImplementationT>,
            private val implClass: Class<ImplementationT>,
            issueReporter: EvalIssueReporter)
    : SealableObject(issueReporter), NamedDomainObjectContainer<InterfaceT> {

    override fun seal() {
        super.seal()
        container.forEach {
            if (it is Sealable) {
                it.seal()
            }
        }
    }

    // wrapper with checkSeal

    override fun create(name: String?): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun create(name: String?, closure: Closure<*>?): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, closure)
    }

    override fun create(name: String?, action: Action<in InterfaceT>?): InterfaceT {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, action)
    }

    override fun addAll(elements: Collection<InterfaceT>): Boolean {
        if (checkSeal()) {
            val recastedElements = elements.stream()
                    .filter(implClass::isInstance)
                    .map(implClass::cast)
                    .collect(Collectors.toSet())

            if (recastedElements.size != elements.size) {
                val wrongTypeElements = elements.stream()
                        .filter({ !implClass.isInstance(it) })
                        .collect(Collectors.toSet())

                issueReporter.reportError(EvalIssueReporter.Type.GENERIC,
                        "Expected type ${implClass.name} for items: $wrongTypeElements")
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

    override fun maybeCreate(name: String?): InterfaceT {
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
                issueReporter.reportError(EvalIssueReporter.Type.GENERIC,
                        "Expected type ${implClass.name} for item: $element")
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

    // basic wrappers

    override fun whenObjectRemoved(closure: Closure<*>?) {
        container.whenObjectRemoved(closure)
    }

    override fun whenObjectRemoved(action: Action<in InterfaceT>): Action<in InterfaceT> {
        container.whenObjectRemoved(action)
        return action
    }

    override fun whenObjectAdded(closure: Closure<*>?) {
        container.whenObjectAdded(closure)
    }

    override fun whenObjectAdded(action: Action<in InterfaceT>): Action<in InterfaceT> {
        container.whenObjectAdded(action)
        return action
    }

    override fun getByName(name: String?): InterfaceT = container.getByName(name)

    override fun findByName(name: String?): InterfaceT? = container.findByName(name)

    override fun getRules(): MutableList<Rule> = container.rules

    override fun addRule(p0: String?, p1: Closure<*>?): Rule = container.addRule(p0, p1)

    override fun addRule(p0: String?, p1: Action<String>?): Rule = container.addRule(p0, p1)

    override fun addRule(p0: Rule?): Rule = container.addRule(p0)

    override fun <S : InterfaceT> withType(p0: Class<S>?, p1: Closure<*>?): DomainObjectCollection<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : InterfaceT> withType(p0: Class<S>?): NamedDomainObjectSet<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : InterfaceT> withType(p0: Class<S>?, p1: Action<in S>?): DomainObjectCollection<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun configure(p0: Closure<*>?): NamedDomainObjectContainer<InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matching(p0: Spec<in InterfaceT>?): NamedDomainObjectSet<InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matching(p0: Closure<*>?): NamedDomainObjectSet<InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNamer(): Namer<InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAsMap(): SortedMap<String, InterfaceT> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun iterator(): MutableIterator<InterfaceT> {
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

    override val size: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

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
}