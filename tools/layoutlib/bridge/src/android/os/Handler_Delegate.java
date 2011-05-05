/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.os;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;


/**
 * Delegate overriding selected methods of android.os.Handler
 *
 * Through the layoutlib_create tool, selected methods of Handler have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 *
 */
public class Handler_Delegate {

    // -------- Delegate methods

    @LayoutlibDelegate
    /*package*/ static boolean sendMessageAtTime(Handler handler, Message msg, long uptimeMillis) {
        // get the callback
        IHandlerCallback callback = sCallbacks.get();
        if (callback != null) {
            callback.sendMessageAtTime(handler, msg, uptimeMillis);
        }
        return true;
    }

    // -------- Delegate implementation

    public interface IHandlerCallback {
        void sendMessageAtTime(Handler handler, Message msg, long uptimeMillis);
    }

    private final static ThreadLocal<IHandlerCallback> sCallbacks =
        new ThreadLocal<IHandlerCallback>();

    public static void setCallback(IHandlerCallback callback) {
        sCallbacks.set(callback);
    }

}
