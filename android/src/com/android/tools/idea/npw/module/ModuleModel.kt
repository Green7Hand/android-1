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
package com.android.tools.idea.npw.module

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.ModuleModelData
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectModelData
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.Recipe
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import java.io.File

private val log: Logger get() = logger<ModuleModel>()

abstract class ModuleModel(
  override var templateFile: File?,
  name: String,
  private val commandName: String = "New Module",
  override val isLibrary: Boolean,
  projectModelData: ProjectModelData,
  _template: NamedModuleTemplate = with(projectModelData) { createDefaultTemplateAt(if (!isNewProject) project.basePath!! else "", name) }
) : WizardModel(), ProjectModelData by projectModelData, ModuleModelData {
  final override val template: ObjectProperty<NamedModuleTemplate> = ObjectValueProperty(_template)
  override val formFactor: ObjectProperty<FormFactor> = ObjectValueProperty(FormFactor.MOBILE)
  final override val moduleName = StringValueProperty(name).apply { addConstraint(String::trim) }
  override val androidSdkInfo = OptionalValueProperty<AndroidVersionsInfo.VersionItem>()
  override val moduleTemplateValues = mutableMapOf<String, Any>()
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(projectTemplateDataBuilder)
  abstract val renderer: MultiTemplateRenderer.TemplateRenderer

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(renderer)
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  abstract inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    /**
     * A new system recipe which will should be run from [render] if the new system is used.
     */
    protected abstract val recipe: Recipe
    /**
     * A value which will be logged for Studio usage tracking.
     */
    protected abstract val loggingEvent: RenderLoggingEvent

    @WorkerThread
    override fun init() {
      TemplateValueInjector(moduleTemplateValues)
        .setProjectDefaults(project, false)
        .setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), packageName.get())
        .setLanguage(language.value)
        .setJavaVersion(project)
        .setBuildVersion(androidSdkInfo.value, project, false)

      moduleTemplateValues[ATTR_IS_LIBRARY_MODULE] = isLibrary

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          projectTemplateDataBuilder.apply {
            setProjectDefaults(project)
            language = this@ModuleModel.language.value
          }
          formFactor = this@ModuleModel.formFactor.get().toTemplateFormFactor()
          isNew = true
          setBuildVersion(androidSdkInfo.value, project)
          setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@ModuleModel.packageName.get())
          isLibrary = this@ModuleModel.isLibrary
        }
      }
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      // This is done because module needs to know about all included form factors, and currently we know about them only after init run,
      // so we need to set it again after all inits (thus in dryRun) TODO(qumeric): remove after adding formFactors to the project
      moduleTemplateValues.putAll(projectTemplateValues)

      // Returns false if there was a render conflict and the user chose to cancel creating the template
      return renderTemplate(true)
    }

    @WorkerThread
    override fun render() {
      val success = WriteCommandAction.writeCommandAction(project).withName(commandName).compute<Boolean, Exception> {
        renderTemplate(false)
      }

      if (!success) {
        log.warn("A problem occurred while creating a new Module. Please check the log file for possible errors.")
      }
    }

    protected open fun renderTemplate(dryRun: Boolean): Boolean {
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName.get())

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        val context = RenderingContext2(
          project = project,
          module = null,
          commandName = commandName,
          templateData = moduleTemplateDataBuilder.build(),
          moduleRoot = moduleRoot,
          dryRun = dryRun,
          showErrors = true
        )

        // TODO(qumeric) We should really only have one root - Update RenderingContext2 to get it from templateData?
        // assert(moduleRoot == (context.templateData as ModuleTemplateData).rootDir)

        val executor = if (dryRun) FindReferencesRecipeExecutor2(context) else DefaultRecipeExecutor2(context)
        return recipe.render(context, executor, loggingEvent)
      }

      val projectRoot = File(project.basePath!!)
      val template = Template.createFromPath(templateFile!!)
      val filesToOpen = mutableListOf<File>()

      val context = Builder.newContext(template, project)
        .withCommandName(commandName)
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .withParams(moduleTemplateValues)
        .intoOpenFiles(filesToOpen)
        .build()

      return template.render(context!!, dryRun).also {
        if (it && !dryRun) {
          // calling smartInvokeLater will make sure that files are open only when the project is ready
          DumbService.getInstance(project).smartInvokeLater { openEditors(project, filesToOpen, false) }
        }
      }
    }
  }
}
/**
 * Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user can later customize
 * the Module Path (called Project Path in gradle world) in "settings.gradle"
 */
fun getModuleRoot(projectLocation: String, moduleName: String) = File(projectLocation, moduleName.replace(':', File.separatorChar))
