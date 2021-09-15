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

package com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun checkoutActivityJava(
  activityClass: String,
  viewModelClass: String,
  layoutName: String,
  packageName: String,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported)
     """layoutBinding = ${layoutToViewBindingClass(layoutName)}.inflate(getLayoutInflater());
     setContentView(layoutBinding.getRoot());
  """ else "setContentView(R.layout.$layoutName);"

  val googlePayButtonBlock = if (isViewBindingSupported)
    "googlePayButton = layoutBinding.googlePayButton.getRoot();"
  else "googlePayButton = findViewById(R.id.googlePayButton);"

  return """
package $packageName;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.PaymentData;

import $packageName.R;
import $packageName.viewmodel.$viewModelClass;

import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

/**
 * Checkout implementation for the app
 */
public class $activityClass extends AppCompatActivity {

  // Arbitrarily-picked constant integer you define to track a request for payment data activity.
  private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;

  private $viewModelClass model;

  ${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} layoutBinding;
  """}}
  private View googlePayButton;

  /**
   * Initialize the Google Pay API on creation of the activity
   *
   * @see AppCompatActivity#onCreate(android.os.Bundle)
   */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initializeUi();

    model = new ViewModelProvider(this).get($viewModelClass.class);
    model.canUseGooglePay.observe(this, this::setGooglePayAvailable);
  }

  private void initializeUi() {

    // Use view binding to access the UI elements
    $contentViewBlock

    // The Google Pay button is a layout file – take the root view
    $googlePayButtonBlock
    googlePayButton.setOnClickListener(this::requestPayment);
  }

  /**
   * If isReadyToPay returned {@code true}, show the button and hide the "checking" text.
   * Otherwise, notify the user that Google Pay is not available. Please adjust to fit in with
   * your current user flow. You are not required to explicitly let the user know if isReadyToPay
   * returns {@code false}.
   *
   * @param available isReadyToPay API response.
   */
  private void setGooglePayAvailable(boolean available) {
    if (available) {
      googlePayButton.setVisibility(View.VISIBLE);
    } else {
      Toast.makeText(this, R.string.googlepay_status_unavailable, Toast.LENGTH_LONG).show();
    }
  }

  public void requestPayment(View view) {

    // Disables the button to prevent multiple clicks.
    googlePayButton.setClickable(false);

    // The price provided to the API should include taxes and shipping.
    // This price is not displayed to the user.
    long dummyPriceCents = 100;
    long shippingCostCents = 900;
    long totalPriceCents = dummyPriceCents + shippingCostCents;
    final Task<PaymentData> task = model.getLoadPaymentDataTask(totalPriceCents);

    // Shows the payment sheet and forwards the result to the onActivityResult method.
    AutoResolveHelper.resolveTask(task, this, LOAD_PAYMENT_DATA_REQUEST_CODE);
  }

  /**
   * Handle a resolved activity from the Google Pay payment sheet.
   *
   * @param requestCode Request code originally supplied to AutoResolveHelper in requestPayment().
   * @param resultCode  Result code returned by the Google Pay API.
   * @param data        Intent from the Google Pay API containing payment or error data.
   * @see <a href="https://developer.android.com/training/basics/intents/result">Getting a result
   * from an Activity</a>
   */
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    switch (requestCode) {
      // value passed in AutoResolveHelper
      case LOAD_PAYMENT_DATA_REQUEST_CODE:
        switch (resultCode) {

          case AppCompatActivity.RESULT_OK:
            PaymentData paymentData = PaymentData.getFromIntent(data);
            handlePaymentSuccess(paymentData);
            break;

          case AppCompatActivity.RESULT_CANCELED:
            // The user cancelled the payment attempt
            break;

          case AutoResolveHelper.RESULT_ERROR:
            Status status = AutoResolveHelper.getStatusFromIntent(data);
            handleError(status);
            break;
        }

        // Re-enables the Google Pay payment button.
        googlePayButton.setClickable(true);
    }
  }

  /**
   * PaymentData response object contains the payment information, as well as any additional
   * requested information, such as billing and shipping address.
   *
   * @param paymentData A response object returned by Google after a payer approves payment.
   * @see <a href="https://developers.google.com/pay/api/android/reference/
   * object#PaymentData">PaymentData</a>
   */
  private void handlePaymentSuccess(@Nullable PaymentData paymentData) {
    final String paymentInfo = paymentData.toJson();

    try {
      JSONObject paymentMethodData = new JSONObject(paymentInfo).getJSONObject("paymentMethodData");
      // If the gateway is set to "example", no payment information is returned - instead, the
      // token will only consist of "examplePaymentMethodToken".

      final JSONObject tokenizationData = paymentMethodData.getJSONObject("tokenizationData");
      final String token = tokenizationData.getString("token");
      final JSONObject info = paymentMethodData.getJSONObject("info");
      final String billingName = info.getJSONObject("billingAddress").getString("name");
      Toast.makeText(
          this, getString(R.string.payments_show_name, billingName),
          Toast.LENGTH_LONG).show();

      // Logging token string.
      Log.d("Google Pay token: ", token);

    } catch (JSONException e) {
      throw new RuntimeException("The selected garment cannot be parsed from the list of elements");
    }
  }

  /**
   * At this stage, the user has already seen a popup informing them an error occurred. Normally,
   * only logging is required.
   *
   * @param status will hold the value of any constant from CommonStatusCode or one of the
   *                   WalletConstants.ERROR_CODE_* constants.
   * @see <a href="https://developers.google.com/android/reference/com/google/android/gms/wallet/
   * WalletConstants#constant-summary">Wallet Constants Library</a>
   */
  private void handleError(@Nullable Status status) {
    String errorString = "Unknown error.";
    if (status != null) {
      int statusCode = status.getStatusCode();
      errorString = String.format(Locale.getDefault(), "Error code: %d", statusCode);
    }

    Log.e("loadPaymentData failed", errorString);
  }
}
"""
}
