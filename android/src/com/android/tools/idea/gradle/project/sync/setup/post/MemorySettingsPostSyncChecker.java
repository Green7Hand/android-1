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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.tools.idea.memorysettings.MemorySettingsUtil;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.util.Locale;
import org.jetbrains.android.util.AndroidBundle;

/**
 * A class to check memory settings after project sync. It may recommend new
 * memory settings based on available RAM of the machine and the project size.
 */
public class MemorySettingsPostSyncChecker {
  private static final Logger LOG = Logger.getInstance(MemorySettingsPostSyncChecker.class);
  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup(
    "Memory Settings Notification",
    NotificationDisplayType.STICKY_BALLOON,
    true);

  /**
   * Checks memory settings and shows a notification if new memory settings
   * are recommended: users may save the recommended settings and restart,
   * or go to memory settings configuration dialog to configure themselves,
   * or set a reminder in a day, or completely ignore the notification.
   * */
  public static void checkSettings(
    Project project,
    TimeBasedMemorySettingsCheckerReminder reminder) {
    LOG.info(String.format(Locale.US, "64bits? : %b, current: %d, available RAM: %d",
                           SystemInfo.is64Bit,
                           MemorySettingsUtil.getCurrentXmx(),
                           MemorySettingsUtil.getMachineMem()));
    if (!MemorySettingsUtil.memorySettingsEnabled()
        || !reminder.shouldCheck(project)
        || hasNotification(project)) {
      LOG.info("Skipped checking memory settings");
      return;
    }

    int currentXmx = MemorySettingsUtil.getCurrentXmx();
    int recommended = getRecommended(project, currentXmx);
    if (recommended > 0) {
      showNotification(project, currentXmx, recommended, reminder);
    }
  }

  // Returns a new Xmx if a recommendation exists, or -1 otherwise.
  static int getRecommended(Project project, int currentXmx) {
    // TODO: check performance to count libraries to see if use it.
    int basedOnMachine = getRecommendedBasedOnMachine();
    int basedOnProject = getRecommendedBasedOnModuleCount(project);
    int recommended = Math.min(basedOnMachine, basedOnProject);
    if (basedOnMachine >= 2048 && recommended < 2048) {
      // For machines with at least 8GB RAB, recommend at least 2GB
      recommended = 2048;
    }
    LOG.info(String.format(Locale.US, "recommendation based on machine: %d, on project: %d",
                           basedOnMachine, basedOnProject));
    return currentXmx < recommended * 0.9 ? recommended : -1;
  }

  private static int getRecommendedBasedOnMachine() {
    int machineMemInGB = MemorySettingsUtil.getMachineMem() >> 10;
    if (machineMemInGB < 8) {
      return 1536;
    } else if (machineMemInGB < 12) {
      return 2048;
    } else if (machineMemInGB < 16) {
      return 3072;
    } else {
      return 4096;
    }
  }

  private static int getRecommendedBasedOnModuleCount(Project project) {
    int count = ModuleManager.getInstance(project).getModules().length;
    if (count < 50) {
      return 1280;
    } else if (count < 100) {
      return 2048;
    } else if (count < 200) {
      return 3072;
    } else {
      return 4096;
    }
  }

  private static boolean hasNotification(Project project) {
    return NotificationsManager
             .getNotificationsManager()
             .getNotificationsOfType(MemorySettingsNotification.class, project)
             .length > 0;
  }

  private static void showNotification(
    Project project,
    int currentXmx,
    int recommended,
    TimeBasedMemorySettingsCheckerReminder reminder) {
    Notification notification = new MemorySettingsNotification(
      AndroidBundle.message("memory.settings.postsync.message",
                            String.valueOf(currentXmx),
                            String.valueOf(recommended)));
    notification.setTitle(AndroidBundle.message("memory.settings.postsync.title"));

    NotificationAction saveRestartAction =
      NotificationAction.createSimple("Save and restart", () -> {
        MemorySettingsUtil.saveXmx(recommended);
        Application app = ApplicationManager.getApplication();
        if (app instanceof ApplicationEx) {
          ((ApplicationEx)app).restart(true);
        }
      });
    NotificationAction configAction =
      NotificationAction.createSimple("Configure", () -> {
        ShowSettingsUtilImpl.showSettingsDialog(project,"memory.settings", "");
        notification.expire();
      });

    notification.addAction(saveRestartAction);
    notification.addAction(configAction);
    notification.addAction(
      new NotificationAction(AndroidBundle.message("memory.settings.postsync.later")) {
        @Override
        public void actionPerformed(AnActionEvent e, Notification n) {
          n.expire();
          reminder.storeLastCheckTimestamp(project);
        }
      });
    notification.addAction(
      new NotificationAction(AndroidBundle.message("memory.settings.do.not.ask")) {
        @Override
        public void actionPerformed(AnActionEvent e, Notification n) {
          n.expire();
          reminder.setDoNotAsk(project);
        }
      });
    notification.notify(project);
  }

  static class MemorySettingsNotification extends Notification {
    public MemorySettingsNotification(String content) {
      super(NOTIFICATION_GROUP.getDisplayId(), "Memory Settings", content, NotificationType.INFORMATION);
    }
  }
}
