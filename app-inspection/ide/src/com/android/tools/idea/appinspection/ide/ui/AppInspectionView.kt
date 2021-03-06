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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.ide.model.AppInspectionTargetsComboBoxModel
import com.intellij.ide.plugins.newui.VerticalLayout
import javax.swing.JPanel

class AppInspectionView {
  val component = JPanel(VerticalLayout(0))
  private val comboBoxModel = AppInspectionTargetsComboBoxModel.newInstance()

  init {
    component.border = AdtUiUtils.DEFAULT_RIGHT_BORDER
    component.add(AppInspectionTargetsComboBox(comboBoxModel))
  }
}