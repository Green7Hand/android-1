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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class ConfigurationTimeReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testConfigurationTimesReport() {

    val analyzerResults = object : MockResultsProvider() {
      override fun getProjectsConfigurationData(): List<ProjectConfigurationData> = listOf(
        project(":app", 1000, listOf(
          plugin(pluginA, 200),
          plugin(pluginB, 100),
          plugin(pluginC, 700)
        )),
        project(":app:nested", 2000, listOf(
          plugin(pluginB, 1500, listOf(
            plugin(applicationPlugin, 800, listOf(
              plugin(libraryPlugin, 500)
            ))
          ))
        )),
        project(":lib", 500, listOf(
          plugin(pluginA, 200),
          plugin(libraryPlugin, 300)
        ))
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345).build()

    assertThat(report.configurationTime.totalConfigurationTime.timeMs).isEqualTo(3500)
    assertThat(report.configurationTime.projects.size).isEqualTo(3)
    assertThat(report.configurationTime.projects[0].configurationTime).isEqualTo(TimeWithPercentage(2000, 3500))
    assertThat(report.configurationTime.projects[0].project).isEqualTo(":app:nested")
    assertThat(report.configurationTime.projects[1].configurationTime).isEqualTo(TimeWithPercentage(1000, 3500))
    assertThat(report.configurationTime.projects[1].project).isEqualTo(":app")
    assertThat(report.configurationTime.projects[2].configurationTime).isEqualTo(TimeWithPercentage(500, 3500))
    assertThat(report.configurationTime.projects[2].project).isEqualTo(":lib")

    assertThat(report.configurationTime.projects[0].plugins.size).isEqualTo(1)
    assertThat(report.configurationTime.projects[0].plugins[0].pluginName).isEqualTo(pluginB.displayName)
    assertThat(report.configurationTime.projects[0].plugins[0].nestedPlugins.size).isEqualTo(1)
    assertThat(report.configurationTime.projects[0].plugins[0].nestedPlugins[0].pluginName).isEqualTo(applicationPlugin.displayName)
    assertThat(report.configurationTime.projects[0].plugins[0].nestedPlugins[0].nestedPlugins.size).isEqualTo(1)
    assertThat(report.configurationTime.projects[0].plugins[0].nestedPlugins[0].nestedPlugins[0].pluginName).isEqualTo(libraryPlugin.displayName)

    assertThat(report.configurationTime.projects[1].plugins.size).isEqualTo(3)
    assertThat(report.configurationTime.projects[1].plugins[0].pluginName).isEqualTo(pluginC.displayName)
    assertThat(report.configurationTime.projects[1].plugins[0].configurationTime).isEqualTo(TimeWithPercentage(700, 3500))
    assertThat(report.configurationTime.projects[1].plugins[1].pluginName).isEqualTo(pluginA.displayName)
    assertThat(report.configurationTime.projects[1].plugins[1].configurationTime).isEqualTo(TimeWithPercentage(200, 3500))
    assertThat(report.configurationTime.projects[1].plugins[2].pluginName).isEqualTo(pluginB.displayName)
    assertThat(report.configurationTime.projects[1].plugins[2].configurationTime).isEqualTo(TimeWithPercentage(100, 3500))

    assertThat(report.configurationTime.projects[2].plugins.size).isEqualTo(2)
    assertThat(report.configurationTime.projects[2].plugins[0].pluginName).isEqualTo(libraryPlugin.displayName)
    assertThat(report.configurationTime.projects[2].plugins[0].configurationTime).isEqualTo(TimeWithPercentage(300, 3500))
    assertThat(report.configurationTime.projects[2].plugins[1].pluginName).isEqualTo(pluginA.displayName)
    assertThat(report.configurationTime.projects[2].plugins[1].configurationTime).isEqualTo(TimeWithPercentage(200, 3500))
  }

  private fun plugin(pluginData: PluginData, duration: Long, nested: List<PluginConfigurationData> = emptyList()) = PluginConfigurationData(
    pluginData, Duration.ofMillis(duration), nested
  )

  private fun project(name: String, duration: Long, plugins: List<PluginConfigurationData> = emptyList()) = ProjectConfigurationData (
    plugins, name, Duration.ofMillis(duration)
  )

}