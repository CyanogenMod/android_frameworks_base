/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2014 SlimRoms Project
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

package com.android.systemui.slimrecent;

import android.content.Intent;
import android.content.pm.ResolveInfo;

public final class TaskDescription {
    final ResolveInfo resolveInfo;
    final int taskId; // application task id for curating apps
    final int persistentTaskId; // persistent id
    final Intent intent; // launch intent for application
    final String packageName; // used to override animations (see onClick())
    final CharSequence description;

    private String mLabel; // application package label
    private int mExpandedState;

    public TaskDescription(int _taskId, int _persistentTaskId,
            ResolveInfo _resolveInfo, Intent _intent,
            String _packageName, CharSequence _description, int expandedState) {
        resolveInfo = _resolveInfo;
        intent = _intent;
        taskId = _taskId;
        persistentTaskId = _persistentTaskId;

        description = _description;
        packageName = _packageName;

        mExpandedState = expandedState;
    }

    public TaskDescription() {
        resolveInfo = null;
        intent = null;
        taskId = -1;
        persistentTaskId = -1;

        description = null;
        packageName = null;
    }

    public boolean isNull() {
        return resolveInfo == null;
    }

    public String getLabel() {
        return mLabel;
    }

    public void setLabel(String label) {
        mLabel = label;
    }

    public int getExpandedState() {
        return mExpandedState;
    }

    public void setExpandedState(int expandedState) {
        mExpandedState = expandedState;
    }
}
