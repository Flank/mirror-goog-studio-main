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
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun loginActivityJava(
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(getLayoutInflater());
     setContentView(binding.getRoot());
  """ else "setContentView(R.layout.$layoutName);"

  return """package  ${packageName}.ui.login;

import android.app.Activity;
import ${getMaterialComponentName("android.arch.lifecycle.Observer", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)};
import android.os.Bundle;
import ${getMaterialComponentName("android.support.annotation.Nullable", useAndroidX)};
import ${getMaterialComponentName("android.support.annotation.StringRes", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import ${packageName}.R;
import ${packageName}.ui.login.LoginViewModel;
import ${packageName}.ui.login.LoginViewModelFactory;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

public class LoginActivity extends AppCompatActivity {

    private LoginViewModel loginViewModel;
${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        $contentViewBlock
        loginViewModel = new ViewModelProvider(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "username")};
        final EditText passwordEditText = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "password")};
        final Button loginButton = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "login")};
        final ProgressBar loadingProgressBar = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "loading")};

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, new Observer<LoginResult>() {
            @Override
            public void onChanged(@Nullable LoginResult loginResult) {
                if (loginResult == null) {
                    return;
                }
                loadingProgressBar.setVisibility(View.GONE);
                if (loginResult.getError() != null) {
                    showLoginFailed(loginResult.getError());
                }
                if (loginResult.getSuccess() != null) {
                    updateUiWithUser(loginResult.getSuccess());
                }
                setResult(Activity.RESULT_OK);

                //Complete and destroy login activity once successful
                finish();
            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    loginViewModel.login(usernameEditText.getText().toString(),
                            passwordEditText.getText().toString());
                }
                return false;
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadingProgressBar.setVisibility(View.VISIBLE);
                loginViewModel.login(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        });
    }

    private void updateUiWithUser(LoggedInUserView model) {
        String welcome = getString(R.string.welcome) + model.getDisplayName();
        // TODO : initiate successful logged in experience
        Toast.makeText(getApplicationContext(), welcome, Toast.LENGTH_LONG).show();
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
    }
}
"""
}
