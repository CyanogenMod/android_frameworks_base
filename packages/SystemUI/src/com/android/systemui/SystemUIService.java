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

package com.android.systemui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import android.util.Log;
import com.android.systemui.cm.HeadsetStatusCallback;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class SystemUIService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        ((SystemUIApplication) getApplication()).startServicesIfNeeded();

        // Starting the headset status handler
        // TODO: Move this in an other Service ? (May be not battery friendly)
        HandlerThread handlerThread = new HandlerThread("Headset-Status-Listener");
        handlerThread.start();

        AudioManager audioManager = ((AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE));
        audioManager.registerAudioDeviceCallback(new HeadsetStatusCallback(this), new Handler(handlerThread.getLooper()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        SystemUI[] services = ((SystemUIApplication) getApplication()).getServices();
        if (args == null || args.length == 0) {
            for (SystemUI ui: services) {
                pw.println("dumping service: " + ui.getClass().getName());
                ui.dump(fd, pw, args);
            }
        } else {
            String svc = args[0];
            for (SystemUI ui: services) {
                String name = ui.getClass().getName();
                if (name.endsWith(svc)) {
                    ui.dump(fd, pw, args);
                }
            }
        }
    }
}

