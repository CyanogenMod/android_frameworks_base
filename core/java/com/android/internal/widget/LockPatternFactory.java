/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.view.Gravity;

public class LockPatternFactory {
    public static LockPattern inject(FrameLayout container) {
        Context context = container.getContext();
        ContentResolver resolver = context.getContentResolver();

        LockPatternUtils utils = new LockPatternUtils(resolver);

        LockPattern pattern = utils.isPinLockingEnabled()
            ? new PinLock(context)
            : new DragLock(context);

        container.addView(
            pattern.getView(),
            new FrameLayout.LayoutParams(
                LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT,
                Gravity.CENTER));

        return pattern;
    }
}

