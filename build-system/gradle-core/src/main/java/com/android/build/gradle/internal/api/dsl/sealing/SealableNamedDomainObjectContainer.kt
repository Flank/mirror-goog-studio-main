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
 * @param B the base interface exposed by the sealable container
 * @param I the actual implementation that the wrapped container contains
 *
 * @see SealableObject
 */
class SealableNamedDomainObjectContainer<B, I: B>(
            private val container: NamedDomainObjectContainer<I>,
            private val implClass: Class<I>,
            issueReporter: EvalIssueReporter)
    : SealableObject(issueReporter), NamedDomainObjectContainer<B> {

    // wrapper with checkSeal

    override fun create(name: String?): B {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun create(name: String?, closure: Closure<*>?): B {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, closure)
    }

    override fun create(name: String?, action: Action<in B>?): B {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, action)
    }

    override fun addAll(elements: Collection<B>): Boolean {
        if (checkSeal()) {
            val recastedElements = elements.stream()
                    .filter(implClass::isInstance)
                    .map(implClass::cast)
                    .collect(Collectors.toSet())

            if (recastedElements.size != elements.size) {
                val wrongTypeElements = elements.stream()
                        .filter({ !implClass.isInstance(it) })
                        .collect(Collectors.toSet())

                issueReporter.reportError(SyncIssue.TYPE_GENERIC,
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

    override fun maybeCreate(name: String?): B {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun remove(element: B): Boolean {
        if (checkSeal()) {
            return container.remove(element)
        }

        return false
    }

    override fun removeAll(elements: Collection<B>): Boolean {
        if (checkSeal()) {
            return container.removeAll(elements)
        }

        return false
    }

    override fun add(element: B): Boolean {
        if (checkSeal()) {
            return if (implClass.isInstance(element)) {
                container.add(implClass.cast(element))
            } else {
                issueReporter.reportError(SyncIssue.TYPE_GENERIC,
                        "Expected type ${implClass.name} for item: $element")
                false
            }
        }

        return false
    }

    override fun retainAll(elements: Collection<B>): Boolean {
        if (checkSeal()) {
            return container.retainAll(elements)
        }

        return false
    }

    // basic wrappers

    override fun whenObjectRemoved(closure: Closure<*>?) {
        container.whenObjectRemoved(closure)
    }

    override fun whenObjectRemoved(action: Action<in B>?): Action<in B> {
        container.whenObjectRemoved(action)
        return action as Action<in B>
    }

    override fun getRules(): MutableList<Rule> = container.rules

    override fun addRule(p0: String?, p1: Closure<*>?): Rule = container.addRule(p0, p1)

    override fun addRule(p0: String?, p1: Action<String>?): Rule = container.addRule(p0, p1)

    override fun addRule(p0: Rule?): Rule = container.addRule(p0)

    override fun <S : B> withType(p0: Class<S>?, p1: Closure<*>?): DomainObjectCollection<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : B> withType(p0: Class<S>?): NamedDomainObjectSet<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : B> withType(p0: Class<S>?, p1: Action<in S>?): DomainObjectCollection<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun configure(p0: Closure<*>?): NamedDomainObjectContainer<B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matching(p0: Spec<in B>?): NamedDomainObjectSet<B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matching(p0: Closure<*>?): NamedDomainObjectSet<B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNamer(): Namer<B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAsMap(): SortedMap<String, B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun iterator(): MutableIterator<B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(p0: Action<in B>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(p0: Closure<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAt(p0: String?): B {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNames(): SortedSet<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAll(elements: Collection<B>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val size: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun getByName(p0: String?): B {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByName(p0: String?, p1: Closure<*>?): B {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByName(p0: String?, p1: Action<in B>?): B {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun whenObjectAdded(p0: Closure<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun whenObjectAdded(p0: Action<in B>?): Action<in B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(element: B): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findAll(p0: Closure<*>?): MutableSet<B> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findByName(p0: String?): B {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}