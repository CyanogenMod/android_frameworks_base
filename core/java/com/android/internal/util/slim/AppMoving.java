/*
* Copyright (C) 2014 SlimRoms Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.util.slim;

import android.util.Log;

import java.io.File;
import java.io.IOException;

public class AppMoving {
    private static final String TAG = "AppMoving";
    private static final String DISABLE_FILE = "/data/system/no-external-apps";

    public static boolean isEnabled() {
        return !new File(DISABLE_FILE).exists();
    }

    private static void removeFile() {
        new File(DISABLE_FILE).delete();
    }

    private static void createFile() {
        try {
            new File(DISABLE_FILE).createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create " + DISABLE_FILE + ": " + e.getMessage());
        }
    }

    public static synchronized void setEnabled(boolean enable) {
        if (enable) {
            removeFile();
        } else {
            createFile();
        }
    }
}
