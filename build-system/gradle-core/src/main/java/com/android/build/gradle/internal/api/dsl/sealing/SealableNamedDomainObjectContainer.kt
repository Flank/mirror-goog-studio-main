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

class SealableNamedDomainObjectContainer<T>(
            private val container: NamedDomainObjectContainer<T>,
            issueReporter: EvalIssueReporter)
    : SealableObject(issueReporter), NamedDomainObjectContainer<T> {

    // wrapper with checkSeal

    override fun create(name: String?): T {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun create(name: String?, closure: Closure<*>?): T {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, closure)
    }

    override fun create(name: String?, action: Action<in T>?): T {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name, action)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return container.addAll(elements)
        }

        return false
    }

    override fun clear() {
        if (checkSeal()) {
            container.clear()
        }
    }

    override fun maybeCreate(name: String?): T {
        // cant use if (checkSeal) because we need to return something if checkSeal returns false
        checkSeal()
        return container.create(name)
    }

    override fun remove(element: T): Boolean {
        if (checkSeal()) {
            return container.remove(element)
        }

        return false
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return container.removeAll(elements)
        }

        return false
    }

    override fun add(element: T): Boolean {
        if (checkSeal()) {
            return container.add(element)
        }

        return false
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        if (checkSeal()) {
            return container.retainAll(elements)
        }

        return false
    }

    // basic wrappers

    override fun whenObjectRemoved(closure: Closure<*>?) {
        container.whenObjectRemoved(closure)
    }

    override fun whenObjectRemoved(action: Action<in T>?): Action<in T> =
            container.whenObjectRemoved(action)

    override fun getRules(): MutableList<Rule> = container.rules

    override fun addRule(p0: String?, p1: Closure<*>?): Rule = container.addRule(p0, p1)

    override fun addRule(p0: String?, p1: Action<String>?): Rule = container.addRule(p0, p1)

    override fun addRule(p0: Rule?): Rule = container.addRule(p0)

    override fun <S : T> withType(p0: Class<S>?, p1: Closure<*>?): DomainObjectCollection<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : T> withType(p0: Class<S>?): NamedDomainObjectSet<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : T> withType(p0: Class<S>?, p1: Action<in S>?): DomainObjectCollection<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun configure(p0: Closure<*>?): NamedDomainObjectContainer<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matching(p0: Spec<in T>?): NamedDomainObjectSet<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matching(p0: Closure<*>?): NamedDomainObjectSet<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNamer(): Namer<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAsMap(): SortedMap<String, T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun iterator(): MutableIterator<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(p0: Action<in T>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun all(p0: Closure<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAt(p0: String?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNames(): SortedSet<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val size: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun getByName(p0: String?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByName(p0: String?, p1: Closure<*>?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByName(p0: String?, p1: Action<in T>?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun whenObjectAdded(p0: Closure<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun whenObjectAdded(p0: Action<in T>?): Action<in T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(element: T): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findAll(p0: Closure<*>?): MutableSet<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findByName(p0: String?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}