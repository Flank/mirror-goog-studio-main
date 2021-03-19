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

package com.android.tools.idea.wizard.template.impl

import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardTemplateProvider
import com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.androidTVActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.basicActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.blankWearActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.bottomNavigationActivity.bottomNavigationActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.composeActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity.cppEmptyActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.emptyActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.fullscreenActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.googleAdMobAdsActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.googleMapsActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.googleMapsWearActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.loginActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.navigationDrawerActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.primaryDetailFlowTemplate
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.responsiveActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.scrollActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.settingsActivity.settingsActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.tabbedActivityTemplate
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.viewModelActivityTemplate
import com.android.tools.idea.wizard.template.impl.fragments.blankFragment.blankFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment.fullscreenFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.googleAdMobAdsFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.googleMapsFragment.googleMapsFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.listFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.loginFragment.loginFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.modalBottomSheet.modalBottomSheetTemplate
import com.android.tools.idea.wizard.template.impl.fragments.scrollFragment.scrollFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.settingsFragment.settingsFragmentTemplate
import com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment.viewModelFragmentTemplate
import com.android.tools.idea.wizard.template.impl.other.androidManifest.androidManifestTemplate
import com.android.tools.idea.wizard.template.impl.other.appWidget.appWidgetTemplate
import com.android.tools.idea.wizard.template.impl.other.automotiveMediaService.automotiveMediaServiceTemplate
import com.android.tools.idea.wizard.template.impl.other.automotiveMessagingService.automotiveMessagingServiceTemplate
import com.android.tools.idea.wizard.template.impl.other.broadcastReceiver.broadcastReceiverTemplate
import com.android.tools.idea.wizard.template.impl.other.contentProvider.contentProviderTemplate
import com.android.tools.idea.wizard.template.impl.other.customView.customViewTemplate
import com.android.tools.idea.wizard.template.impl.other.files.aidlFile.aidlFileTemplate
import com.android.tools.idea.wizard.template.impl.other.files.appActionsResourceFile.appActionsResourceFileTemplate
import com.android.tools.idea.wizard.template.impl.other.files.layoutResourceFile.layoutResourceFileTemplate
import com.android.tools.idea.wizard.template.impl.other.files.valueResourceFile.valueResourceFileTemplate
import com.android.tools.idea.wizard.template.impl.other.folders.folderTemplates
import com.android.tools.idea.wizard.template.impl.other.intentService.intentServiceTemplate
import com.android.tools.idea.wizard.template.impl.other.service.serviceTemplate
import com.android.tools.idea.wizard.template.impl.other.sliceProvider.sliceProviderTemplate
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.watchFaceServiceTemplate

/**
 * Implementation of the Android Wizard Template plugin extension point.
 */
class WizardTemplateProviderImpl : WizardTemplateProvider() {
  override fun getTemplates(): List<Template> = listOf(
    androidTVActivityTemplate,
    basicActivityTemplate,
    blankWearActivityTemplate,
    bottomNavigationActivityTemplate,
    composeActivityTemplate,
    emptyActivityTemplate,
    fullscreenActivityTemplate,
    googleAdMobAdsActivityTemplate,
    googleMapsActivityTemplate,
    googleMapsWearActivityTemplate,
    loginActivityTemplate,
    primaryDetailFlowTemplate,
    navigationDrawerActivityTemplate,
    responsiveActivityTemplate,
    settingsActivityTemplate,
    scrollActivityTemplate,
    tabbedActivityTemplate,
    viewModelActivityTemplate,
    cppEmptyActivityTemplate, // Keep last as per UX design

    blankFragmentTemplate,
    fullscreenFragmentTemplate,
    googleAdMobAdsFragmentTemplate,
    googleMapsFragmentTemplate,
    listFragmentTemplate,
    loginFragmentTemplate,
    modalBottomSheetTemplate,
    settingsFragmentTemplate,
    scrollFragmentTemplate,
    viewModelFragmentTemplate,

    androidManifestTemplate,
    appWidgetTemplate,
    automotiveMediaServiceTemplate,
    automotiveMessagingServiceTemplate,
    broadcastReceiverTemplate,
    contentProviderTemplate,
    customViewTemplate,
    intentServiceTemplate,
    serviceTemplate,
    sliceProviderTemplate,
    watchFaceServiceTemplate
  ) + folderTemplates + fileTemplates

  private val fileTemplates = listOf(
    aidlFileTemplate,
    appActionsResourceFileTemplate,
    layoutResourceFileTemplate,
    valueResourceFileTemplate
  )
}
