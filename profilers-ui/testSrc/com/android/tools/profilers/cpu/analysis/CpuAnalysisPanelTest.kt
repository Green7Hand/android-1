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
package com.android.tools.profilers.cpu.analysis

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.android.tools.profilers.cpu.FakeCpuService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CpuAnalysisPanelTest {

  private val timer = FakeTimer()
  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", FakeCpuService(), FakeProfilerService(timer))
  private val profilerClient = ProfilerClient(grpcChannel.name)
  private lateinit var profilers: StudioProfilers
  private val services = FakeIdeProfilerServices()
  private lateinit var stage: CpuCaptureStage
  private lateinit var panel: CpuAnalysisPanel

  @Before
  fun setUp() {
    services.enablePerfetto(true)
    services.enableAtrace(true)
    profilers = StudioProfilers(profilerClient, services, timer)
    stage = CpuCaptureStage.create(profilers, "Test", TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.ATRACE_TRACE_PATH))
    panel = CpuAnalysisPanel(StudioProfilersView(profilers, FakeIdeProfilerComponents()), stage)
  }

  @Test
  fun tabsUpdatedOnCaptureCompleted() {
    val observer = AspectObserver()
    val stateLatch = CountDownLatch(1)
    stage.aspect.addDependency(observer).onChange(CpuCaptureStage.Aspect.STATE, Runnable {
      assertThat(panel.tabView.tabCount).isNotEqualTo(0)
      stateLatch.countDown()
    })
    assertThat(panel.tabView.tabCount).isEqualTo(0)
    stage.enter()
    assertThat(stateLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun newAnalysisIsAutoSelected() {
    stage.enter()
    val selectionModel = CpuAnalysisModel("TEST")
    selectionModel.tabs.add(CpuAnalysisTabModel<CpuCapture>("Summary"))
    selectionModel.tabs.add(CpuAnalysisTabModel<CpuCapture>("Summary2"))
    stage.addCpuAnalysisModel(selectionModel)
    assertThat(panel.tabView.tabCount).isEqualTo(2)
  }

  @Test
  fun tabsAreOnlyPopulatedWhenSelected() {
    stage.enter()
    assertThat(panel.tabView.selectedIndex).isEqualTo(0)
    assertThat(panel.tabView.getComponentAt(0)).isInstanceOf(CpuAnalysisSummaryTab::class.java)
    assertThat(panel.tabView.getComponentAt(1)).isNotInstanceOf(CpuAnalysisChart::class.java)
    panel.tabView.selectedIndex = 1
    assertThat(panel.tabView.getComponentAt(0)).isNotInstanceOf(CpuAnalysisSummaryTab::class.java)
    assertThat(panel.tabView.getComponentAt(1)).isInstanceOf(CpuAnalysisChart::class.java)
  }
}