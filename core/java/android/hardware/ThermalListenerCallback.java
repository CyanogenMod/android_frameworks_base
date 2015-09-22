/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package android.hardware;

public abstract class ThermalListenerCallback extends IThermalListenerCallback.Stub {
    public static final class State {
        public static final int STATE_UNKNOWN = -1;
        public static final int STATE_COOL = 0;
        public static final int STATE_NORMAL = 1;
        public static final int STATE_HIGH = 2;
        public static final int STATE_EXTREME = 3;
        public static final String toString(int state) {
            switch (state) {
                case STATE_COOL:
                    return "STATE_COOL";
                case STATE_NORMAL:
                    return "STATE_NORMAL";
                case STATE_HIGH:
                    return "STATE_HIGH";
                case STATE_EXTREME:
                    return "STATE_EXTREME";
                default:
                    return "STATE_UNKNOWN";
            }
        }
    }
}
