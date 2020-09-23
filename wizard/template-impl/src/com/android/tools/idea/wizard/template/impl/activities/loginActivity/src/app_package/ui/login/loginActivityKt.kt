/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun loginActivityKt(
  activityClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
     setContentView(binding.root)
  """ else "setContentView(R.layout.$layoutName)"

  return """
package ${escapeKotlinIdentifier(packageName)}.ui.login

import android.app.Activity
import ${getMaterialComponentName("android.arch.lifecycle.Observer", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)}
import android.os.Bundle
import ${getMaterialComponentName("android.support.annotation.StringRes", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
${renderIf(!isViewBindingSupported) {"""import android.widget.Button"""}}
import android.widget.EditText
${renderIf(!isViewBindingSupported) {"""import android.widget.ProgressBar"""}}
import android.widget.Toast
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}

import ${escapeKotlinIdentifier(packageName)}.R

class ${activityClass} : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
${renderIf(isViewBindingSupported) {"""
    private lateinit var binding: ${layoutToViewBindingClass(layoutName)}
"""}}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        $contentViewBlock

        val username = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "username",
          className = "EditText")}
        val password = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "password",
          className = "EditText")}
        val login = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "login",
          className = "Button")}
        val loading = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "loading",
          className = "ProgressBar")}

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())
            .get(LoginViewModel::class.java)

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
               password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                updateUiWithUser(loginResult.success)
            }
            setResult(Activity.RESULT_OK)

            //Complete and destroy login activity once successful
            finish()
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                loginViewModel.login(username.text.toString(), password.text.toString())
            }
        }
    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome)
        val displayName = model.displayName
        // TODO : initiate successful logged in experience
        Toast.makeText(
            applicationContext,
            "${"$"}welcome ${"$"}displayName",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
"""
}
