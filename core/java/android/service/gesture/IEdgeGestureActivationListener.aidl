/*
 * Copyright (C) 2013-2015 The CyanogenMod Project (Jens Doll)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.service.gesture;

import android.view.InputEvent;

/** @hide */
interface IEdgeGestureActivationListener {

    /** Called when a gesture is detected that fits to the activation gesture. At this point in
     * time gesture detection is disabled. Call IEdgeGestureHostCallback.restoreState() to
     * recover from this.
     */
    oneway void onEdgeGestureActivation(int touchX, int touchY, int positionIndex, int flags);
}
