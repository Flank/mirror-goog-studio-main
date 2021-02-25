/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_INTENT
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Flags activity launches via broadcast receivers and services, which
 * will be forbidden from Android S (and is already a bad idea for
 * performance, which is why we're going to block it.)
 */
class NotificationTrampolineDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            NotificationTrampolineDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        /** Launching activities indirectly from activities. */
        @JvmField
        val TRAMPOLINE = Issue.create(
            id = "NotificationTrampoline",
            briefDescription = "Notification Trampolines",
            explanation = """
                Activities should not be launched indirectly from a notification via an \
                intermediate `BroadcastReceiver` or `Service`. This can lead to significant lags \
                in some scenarios, and is forbidden if `targetSdkVersion` is set to the API level \
                for Android 12 or higher.

                To fix this, launch the `Activity` directly from the notification.
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /**
         * Launching services or broadcast receivers from a whole
         * notification.
         */
        @JvmField
        val ACTIVITY = Issue.create(
            id = "LaunchActivityFromNotification",
            briefDescription = "Notification Launches Services or BroadcastReceivers",
            explanation = """
                Notifications should only launch activities -- that's what users expect (and \
                has been the guidance in both the Android SDK and Material Design documentation \
                for a while).

                A `Service` or a `BroadcastReceiver` should not be used as an intermediate, \
                because this can lead to significant performance problems, and as a \
                result, this will not be allowed in Android 12.
                """,
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
            .addMoreInfo("https://developer.android.com/guide/topics/ui/notifiers/notifications?hl=en#Actions")
            .addMoreInfo("https://material.io/design/platform-guidance/android-notifications.html#behavior")
            .addMoreInfo("https://developer.android.com/guide/topics/ui/notifiers/notifications?hl=en")
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setContentIntent", "setFullScreenIntent", "addAction"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Algorithm:
        // Look for either of these calls on NotificationCompat.Builder:
        //     setContentIntent(pendingIntent) or addAction(_,_,pendingIntent)
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "androidx.core.app.NotificationCompat.Builder")) {
            return
        }

        // Once these have been identified, chase back to the pendingIntent declaration.
        val pendingConstruction = findPendingIntentConstruction(node) ?: return

        // If the intent is a broadcast receiver, returns true, if it's a service, returns
        // false, and if it's neither, return null:
        val isBroadcastReceiver = isBroadcastReceiver(pendingConstruction) ?: return

        val foundClass = checkNonActivityIntent(
            context, pendingConstruction, node, isBroadcastReceiver
        )
        if (!foundClass && getMethodName(node) != "addAction") {
            reportNonActivityIntent(context, node, pendingConstruction, isBroadcastReceiver)
        }
    }

    /**
     * The notification is associated with a broadcast receiver; look at
     * the given receiver and see if it launches any activities (if so,
     * report the notification construction with an error), and either
     * way, return true if we found the receiver class.
     */
    private fun checkNonActivityIntent(
        context: JavaContext,
        pendingConstruction: UCallExpression,
        node: UCallExpression,
        isBroadcastReceiver: Boolean
    ): Boolean {
        // find the declaration of the intent argument
        val intentConstruction = findIntentConstruction(pendingConstruction) ?: return false

        // Then in the intent declaration, find the associated broadcast receiver class:
        //     Intent intent = new Intent(_, receiver.class);
        val classArgument = findClassArgument(intentConstruction) ?: return false
        val nonActivityClass = findClassFromLiteral(classArgument) ?: return false

        // Check the receiver class: look up its onReceive method, and if we find an
        // occurrence of startActivity() or startActivities(), flag it as an error
        // Or error?
        return if (isBroadcastReceiver) {
            checkBroadcastReceiver(context, nonActivityClass, node)
        } else {
            checkService(context, nonActivityClass, node)
        }
    }

    private fun findPendingIntentConstruction(node: UCallExpression): UCallExpression? {
        val methodName = getMethodName(node)
        val pendingIntentArgument = if (methodName == "addAction")
            node.getArgumentForParameter(2) ?: return null
        else
            node.getArgumentForParameter(0) ?: return null

        return findPendingIntentConstruction(pendingIntentArgument, node)
    }

    private fun isBroadcastReceiver(pendingConstruction: UCallExpression): Boolean? {
        return when (getMethodName(pendingConstruction)) {
            "getBroadcast" -> true
            "getService" -> false
            else -> null // not a broadcast receiver or service
        }
    }

    private fun findPendingIntentConstruction(
        pendingIntentArgument: UExpression,
        node: UElement
    ): UCallExpression? {
        when (val resolved = pendingIntentArgument.tryResolve()) {
            is PsiVariable -> {
                return findLastAssignment(resolved, node)
                    ?.findSelector() as? UCallExpression
            }
            is PsiMethod -> {
                if (resolved.containingFile is PsiBinaryFile) {
                    val name = resolved.containingClass?.qualifiedName
                    if (name == "android.app.PendingIntent") {
                        if (pendingIntentArgument is UCallExpression) {
                            return pendingIntentArgument
                        } else if (pendingIntentArgument is UQualifiedReferenceExpression) {
                            val selector = pendingIntentArgument.selector
                            if (selector is UCallExpression) {
                                return selector
                            }
                        }
                    }
                    return null
                }
                val method = resolved.toUElementOfType<UMethod>() ?: return null
                val ref = Ref<UCallExpression>()
                method.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        node.returnExpression?.let {
                            val construction = findPendingIntentConstruction(it, node)
                            if (construction != null && isBroadcastReceiver(construction) != null) {
                                ref.set(construction)
                            }
                        }
                        return super.visitReturnExpression(node)
                    }
                })
                return ref.get()
            }
            else -> {
                return null
            }
        }
    }

    private fun findIntentConstruction(pendingConstruction: UCallExpression): UCallExpression? {
        val getBroadcastMethod = pendingConstruction.resolve() ?: return null
        if (getBroadcastMethod.parameterList.parametersCount != 4) {
            return null
        }
        val intentArg = pendingConstruction.getArgumentForParameter(2) ?: return null
        val intentDeclaration = intentArg.tryResolve() as? PsiVariable ?: return null
        val intentAssignment = findLastAssignment(intentDeclaration, pendingConstruction) ?: return null
        return intentAssignment as? UCallExpression
    }

    private fun findClassArgument(constructorCall: UCallExpression): UElement? {
        val intentConstructor = constructorCall.resolve() ?: return null
        val constructorParameters = intentConstructor.parameters
        val classParameterIndex =
            when (constructorParameters.size) {
                // Intent(Context packageContext, Class<?> cls)
                2 -> 1
                // Intent(String action, Uri uri, Context packageContext, Class<?> cls)
                3 -> 2
                else -> return null
            }
        val classParameterType = constructorParameters[classParameterIndex]
        val type = classParameterType.type as? PsiClassType ?: return null
        if (type.canonicalText != "java.lang.Class<?>") {
            return null
        }

        return constructorCall.getArgumentForParameter(classParameterIndex)?.findSelector()
    }

    /**
     * Given a Foo.class or Foo::class.java class literal in Java or
     * Kotlin, return the resolved Foo class, unless it's a binary class
     * (bytecode)
     */
    private fun findClassFromLiteral(argument: UElement): PsiClass? {
        val type =
            if (argument is UClassLiteralExpression) {
                argument.type
            } else if (argument is UQualifiedReferenceExpression &&
                argument.resolvedName == "getJavaClass"
            ) {
                // Kotlin syntax: Foo::class.java
                (argument.receiver as UClassLiteralExpression).type
            } else if (argument is USimpleNameReferenceExpression &&
                argument.uastParent is UQualifiedReferenceExpression
            ) {
                val receiver = (argument.uastParent as UQualifiedReferenceExpression).receiver
                (receiver as? UClassLiteralExpression)?.type ?: return null
            } else {
                return null
            }

        val resolved = (type as? PsiClassType)?.resolve() ?: return null
        if (resolved.containingFile is PsiBinaryFile) return null
        return resolved
    }

    /**
     * Given a broadcast receiver, check whether its `onReceive` method
     * starts activities, and if so flag them as errors. Return true if
     * the receiver class was found (though if it was, and we can't get
     * the UAST for the onReceive method, return false.)
     */
    private fun checkBroadcastReceiver(
        context: JavaContext,
        broadcastClass: PsiClass,
        setPendingIntent: UCallExpression
    ): Boolean {
        val methods = broadcastClass.findMethodsByName("onReceive", false)
        // BroadcastReceiver can have written its own overloads so search for the right one
        val evaluator = context.evaluator
        for (member in methods) {
            if (evaluator.parametersMatch(member, CLASS_CONTEXT, CLASS_INTENT)) {
                return checkReceiverOrService(
                    context, member, setPendingIntent, broadcastClass,
                    "BroadcastReceiver"
                )
            }
        }
        return true
    }

    /**
     * Given a service, check whether its `onStartCommand` method starts
     * activities, and if so flag them as errors. Return true if the
     * service class was found (though if it was, and we can't get the
     * UAST for the onStartCommand method, return false.)
     */
    private fun checkService(
        context: JavaContext,
        serviceClass: PsiClass,
        setPendingIntent: UCallExpression
    ): Boolean {
        val methods = serviceClass.findMethodsByName("onStartCommand", false)
        // The service can have written its own overloads so search for the right one
        val evaluator = context.evaluator
        for (member in methods) {
            if (evaluator.parametersMatch(member, CLASS_INTENT, TYPE_INT, TYPE_INT)) {
                return checkReceiverOrService(
                    context, member, setPendingIntent, serviceClass, "Service"
                )
            }
        }
        return true
    }

    private fun checkReceiverOrService(
        context: JavaContext,
        onReceiveMethod: PsiMethod,
        setPendingIntent: UCallExpression,
        broadcastClass: PsiClass,
        description: String
    ): Boolean {
        val method = onReceiveMethod.toUElementOfType<UMethod>() ?: return false
        method.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = getMethodName(node)
                if (methodName == "startActivity" || methodName == "startActivities") {
                    reportServiceOrReceiver(
                        context, node, setPendingIntent, broadcastClass, description
                    )
                }
                return super.visitCallExpression(node)
            }
        })
        return true
    }

    private fun reportServiceOrReceiver(
        context: JavaContext,
        startActivity: UCallExpression,
        setPendingIntent: UCallExpression,
        broadcastClass: PsiClass,
        description: String
    ) {
        val primaryLocation = context.getCallLocation(
            setPendingIntent,
            includeReceiver = false,
            includeArguments = true
        )
        val secondaryLocation = context.getCallLocation(
            startActivity,
            includeReceiver = true,
            includeArguments = true
        )
        primaryLocation.secondary = secondaryLocation

        val message = "This intent launches a `$description` " +
            "(`${broadcastClass.name}`) which launches activities; " +
            "this indirection is bad for performance, and activities " +
            "should be launched directly from the notification"
        context.report(TRAMPOLINE, setPendingIntent, primaryLocation, message)
    }

    private fun reportNonActivityIntent(
        context: JavaContext,
        setPendingIntent: UCallExpression,
        pendingConstruction: UCallExpression,
        isBroadcastReceiver: Boolean
    ) {
        // It's a pending intent associated with a broadcast receiver, but we can't
        // access the receiver as source code (perhaps it's in a jar etc);
        // instead warn users that they should only be launching activities from
        // notifications, **unless** it's a notification action!
        val className = if (isBroadcastReceiver) "BroadcastReceiver" else "Service"
        val message = "Notifications should only launch a `$className` from " +
            "notification actions (`addAction`)"

        val location = context.getCallLocation(
            setPendingIntent,
            includeReceiver = false,
            includeArguments = true
        )
        val secondary = context.getCallLocation(
            pendingConstruction,
            includeReceiver = true,
            includeArguments = true
        )
        secondary.message = "This `$className` intent is launched from a " +
            "notification; this is discouraged except as notification actions"

        location.secondary = secondary
        context.report(ACTIVITY, setPendingIntent, location, message)
    }
}

fun UElement.findSelector(): UElement? {
    var curr = this
    while (curr is UQualifiedReferenceExpression) {
        curr = curr.selector
    }
    return curr
}
