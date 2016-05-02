/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.systemui.statusbar.policy;

public interface WeatherController {
    void addCallback(Callback callback);
    void removeCallback(Callback callback);
    WeatherInfo getWeatherInfo();

    public interface Callback {
        void onWeatherChanged(WeatherInfo temp);
    }
    public static class WeatherInfo {
        public double temp = Double.NaN;
        public String city = null;
        public String condition = null;
        public int tempUnit;
    }
}
