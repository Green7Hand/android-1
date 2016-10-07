/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Syncs project with Gradle, with an additional argument to refresh the linked C++ projects.
 */
public class RefreshLinkedCppProjectsAction extends SyncProjectAction {
  public static final Key<Boolean> REFRESH_EXTERNAL_NATIVE_MODELS_KEY = Key.create("refresh.external.native.models");

  public RefreshLinkedCppProjectsAction() {
    super("Refresh Linked C++ Projects");
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    project.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);
    super.doPerform(e, project);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    if (!containsExternalCppProjects(project)) {
      e.getPresentation().setEnabled(false);
      return;
    }

    super.doUpdate(e, project);
  }

  private static boolean containsExternalCppProjects(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      NativeAndroidGradleModel nativeAndroidModel = NativeAndroidGradleModel.get(module);
      if (nativeAndroidModel != null) {
        // TODO: Return true only when the model is generated by an external build system.
        return true;
      }
    }
    return false;
  }
}
