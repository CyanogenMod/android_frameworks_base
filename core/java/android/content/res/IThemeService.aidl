/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package android.content.res;

import android.content.res.IThemeChangeListener;
import android.content.res.IThemeProcessingListener;
import android.graphics.Bitmap;

import java.util.Map;

/** {@hide} */
interface IThemeService {
    void requestThemeChangeUpdates(in IThemeChangeListener listener);
    void removeUpdates(in IThemeChangeListener listener);

    void requestThemeChange(in Map componentMap);
    void applyDefaultTheme();
    boolean isThemeApplying();
    int getProgress();

    boolean cacheComposedIcon(in Bitmap icon, String path);

    boolean processThemeResources(String themePkgName);
    boolean isThemeBeingProcessed(String themePkgName);
    void registerThemeProcessingListener(in IThemeProcessingListener listener);
    void unregisterThemeProcessingListener(in IThemeProcessingListener listener);
}
