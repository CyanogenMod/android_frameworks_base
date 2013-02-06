/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy;

import android.content.Context;
import android.view.FallbackEventHandler;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManagerPolicy;

import com.android.internal.os.IDeviceHandler;

/**
 * {@hide}
 */

/* The implementation of this interface must be called Policy and contained
 * within the com.android.internal.policy.impl package */
public interface IPolicy {
    public Window makeNewWindow(Context context);

    public LayoutInflater makeNewLayoutInflater(Context context);

    public WindowManagerPolicy makeNewWindowManager(IDeviceHandler device);

    public FallbackEventHandler makeNewFallbackEventHandler(Context context);
}
