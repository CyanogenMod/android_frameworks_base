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

import android.service.gesture.IEdgeGestureActivationListener;
import android.service.gesture.IEdgeGestureHostCallback;

/** @hide */
interface IEdgeGestureService {

    /** Register a listener for activation gestures. Initially the listener
     * is set to listen for no position. Use updateEdgeGestureActivationListener() to
     * bind the listener to positions.
     * Use the returned IEdgeGestureHostCallback to manipulate the state after activation.
     */
    IEdgeGestureHostCallback registerEdgeGestureActivationListener(
            in IEdgeGestureActivationListener listener);

    /** Update the listener to react on gestures in the given positions.
     */
    void updateEdgeGestureActivationListener(in IBinder listener, int positionFlags);

    /**
     * Reduce left and right detection height if IME keyboard is active.
     */
    void setImeIsActive(in boolean enabled);

    /**
     * If setImeIsActive(boolean enabled) is set
     * temporaly overwrite it for overlaying views like
     * notification drawer or global menu.
     */
    void setOverwriteImeIsActive(in boolean enabled);

}
