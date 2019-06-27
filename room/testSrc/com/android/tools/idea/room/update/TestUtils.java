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
package com.android.tools.idea.room.update;

import com.android.tools.idea.room.bundle.DatabaseBundle;
import com.android.tools.idea.room.bundle.EntityBundle;
import com.android.tools.idea.room.bundle.FieldBundle;
import java.util.List;

/**
 * Utility functions for testing schema updates.
 */
public class TestUtils {
  public static DatabaseBundle createDatabaseBundle(int version, List<EntityBundle> entities) {
    return new DatabaseBundle(version, "", entities, null, null);
  }

  public static FieldBundle createFieldBundle(String columnName) {
    return new FieldBundle("", columnName, "", false, "");
  }

  public static EntityBundle createEntityBundle(String tableName, List<FieldBundle> fields) {
    return new EntityBundle(tableName, "", fields, null, null, null);
  }
}