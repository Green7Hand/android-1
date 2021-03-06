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
package com.android.tools.idea.model

import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.util.Key
import com.intellij.util.indexing.FileContent
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidManifestIndexTest {
  @Test
  fun indexer_reallyShortManifest() {
    val manifest = AndroidManifestIndex.Indexer.computeValue(FakeXmlFileContent("<"))
    assertThat(manifest).isNull()
  }

  @Test
  fun indexer_wellFormedManifest() {
    @Language("xml")
    val manifestContent = """
<?xml version='1.0' encoding='utf-8'?>
<manifest xmlns:android='http://schemas.android.com/apk/res/android' 
  package='com.example' android:debuggable="false" android:enabled='true'>
  <application android:theme='@style/Theme.AppCompat'>
    <activity android:name='.EnabledActivity' android:enabled='true'>
      <intent-filter>
        <action android:name='android.intent.action.MAIN'/>
        <category android:name='android.intent.category.DEFAULT'/>
      </intent-filter>
    </activity>
    <activity android:name='.DisabledActivity' android:enabled='false'>
    </activity>
    <activity-alias android:name='.EnabledAlias' android:enabled='true' android:targetActivity='.DisabledActivity'>
    </activity-alias>
    <activity-alias android:name='.DisabledAlias' android:enabled='false' android:targetActivity='.EnabledActivity'>
    </activity-alias>
  </application>
  <uses-permission android:name='android.permission.SEND_SMS'/>
  <uses-permission-sdk-23 android:name='custom.permissions.NO_GROUP'/>
  <permission-group android:name='custom.permissions.CUSTOM_GROUP'/>
  <permission android:name='custom.permissions.IN_CUSTOM_GROUP' android:permissionGroup='custom.permissions.CUSTOM_GROUP'/>
  <permission android:name='custom.permissions.NO_GROUP'/>
  <uses-sdk android:minSdkVersion='22' android:targetSdkVersion='28'/>
</manifest>
    """.trimIndent()
    val manifest = AndroidManifestIndex.Indexer.computeValue(FakeXmlFileContent(manifestContent))
    assertThat(manifest).isEqualTo(
      AndroidManifestRawText(
        activities = setOf(
          ActivityRawText(
            name = ".EnabledActivity",
            enabled = "true",
            intentFilters = setOf(
              IntentFilterRawText(actionNames = setOf("android.intent.action.MAIN"),
                                  categoryNames = setOf("android.intent.category.DEFAULT"))
            )
          ),
          ActivityRawText(name = ".DisabledActivity", enabled = "false", intentFilters = setOf())
        ),
        activityAliases = setOf(
          ActivityAliasRawText(name = ".EnabledAlias", targetActivity = ".DisabledActivity", enabled = "true", intentFilters = setOf()),
          ActivityAliasRawText(name = ".DisabledAlias", targetActivity = ".EnabledActivity", enabled = "false", intentFilters = setOf())
        ),
        customPermissionGroupNames = setOf("custom.permissions.CUSTOM_GROUP"),
        customPermissionNames = setOf("custom.permissions.IN_CUSTOM_GROUP", "custom.permissions.NO_GROUP"),
        debuggable = "false",
        enabled = "true",
        minSdkLevel = "22",
        packageName = "com.example",
        usedPermissionNames = setOf("android.permission.SEND_SMS", "custom.permissions.NO_GROUP"),
        targetSdkLevel = "28",
        theme = "@style/Theme.AppCompat"
      )
    )
  }

  @Test
  fun indexer_malFormedManifest() {
    @Language("xml")
    val manifestContent = """
<?xml version='1.0' encoding='utf-8'?>
<manifest xmlns:android='http://schemas.android.com/apk/res/android' 
  package='com.example' android:debuggable="false" android:enabled='true'>
  <application android:theme='@style/Theme.AppCompat'>
    <activity android:name='.EnabledActivity' android:enabled='true'>
      <intent-filter>
        <action android:name='android.intent.action.MAIN'/>
        <category android:name='android.intent.category.DEFAULT'/>
        
        <!-- Recovery case1: Though Attr.value missing errors, no more other siblings(child tags of <intent-filter>) 
        need to be processed, we can go to the next END_TAG and then return to its parent tag, <intent-filter> -->
        <action android:name
        
      </intent-filter>
    </activity>
    <activity android:name='.DisabledActivity' android:enabled='false'>
    </activity>
    <activity-alias android:name='.EnabledAlias' android:enabled='true' android:targetActivity='.DisabledActivity'>
    </activity-alias>
    <activity-alias android:name='.DisabledAlias' android:enabled='false' android:targetActivity='.EnabledActivity'>
    </activity-alias>
  </application>
  <uses-permission android:name='android.permission.SEND_SMS'/>
  <uses-permission-sdk-23 android:name='custom.permissions.NO_GROUP'/>
  <permission-group android:name='custom.permissions.CUSTOM_GROUP'/>
  <permission android:name='custom.permissions.IN_CUSTOM_GROUP' android:permissionGroup='custom.permissions.CUSTOM_GROUP'/>
  <permission android:name='custom.permissions.NO_GROUP'/>
  
  <!-- Recovery case2: though Attr.value missing errors, the next sibling, child tag of <manifest> can be processed successfully -->
  <permission android:nam
  
  <uses-sdk android:minSdkVersion='22' android:targetSdkVersion='28'/>
  
  <!-- No recovery case1: though no end tag of uses-permission, info of this tag is retrieved still. However for the rest of the file, 
  parsing won't be recovered because no matching end tag after skipping sub tree(based on the level matching). And eventually, it hits
  the end of document. -->
  <uses-permission android:name='android.permission.SEND_SMS'>
  
  <uses-permission-sdk-23 android:name='custom.permissions.NO_GROUP1'/>
</manifest>
      """.trimIndent()
    val manifest = AndroidManifestIndex.Indexer.computeValue(FakeXmlFileContent(manifestContent))
    assertThat(manifest).isEqualTo(
      AndroidManifestRawText(
        activities = setOf(
          ActivityRawText(
            name = ".EnabledActivity",
            enabled = "true",
            intentFilters = setOf(
              IntentFilterRawText(actionNames = setOf("android.intent.action.MAIN"),
                                  categoryNames = setOf("android.intent.category.DEFAULT"))
            )
          ),
          ActivityRawText(name = ".DisabledActivity", enabled = "false", intentFilters = setOf())
        ),
        activityAliases = setOf(
          ActivityAliasRawText(name = ".EnabledAlias", targetActivity = ".DisabledActivity", enabled = "true", intentFilters = setOf()),
          ActivityAliasRawText(name = ".DisabledAlias", targetActivity = ".EnabledActivity", enabled = "false", intentFilters = setOf())
        ),
        customPermissionGroupNames = setOf("custom.permissions.CUSTOM_GROUP"),
        customPermissionNames = setOf("custom.permissions.IN_CUSTOM_GROUP", "custom.permissions.NO_GROUP"),
        debuggable = "false",
        enabled = "true",
        minSdkLevel = "22",
        packageName = "com.example",
        usedPermissionNames = setOf("android.permission.SEND_SMS", "custom.permissions.NO_GROUP"),
        targetSdkLevel = "28",
        theme = "@style/Theme.AppCompat"
      )
    )
  }
}

private class FakeXmlFileContent(private val content: String) : FileContent {
  private val file = MockVirtualFile("", content)

  override fun getContentAsText() = content
  override fun getContent() = content.toByteArray()
  override fun <T : Any?> getUserData(key: Key<T>): T? = throw UnsupportedOperationException()
  override fun getFileType(): FileType = StdFileTypes.XML
  override fun getFile() = file
  override fun getFileName() = ""
  override fun <T : Any?> putUserData(key: Key<T>, value: T?) = throw UnsupportedOperationException()
  override fun getProject() = throw UnsupportedOperationException()
  override fun getPsiFile() = throw UnsupportedOperationException()
}