/*
 * Copyright (C) 2011, T-Mobile USA, Inc.
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

package com.android.internal.app;

import android.content.res.PackageRedirectionMap;

/**
 * Interface used to interact with the AssetRedirectionManagerService.
 */
interface IAssetRedirectionManager {
    /**
     * Access the package redirection map for the supplied package name given a
     * particular theme.
     */
    PackageRedirectionMap getPackageRedirectionMap(in String themePackageName,
            String themeId, in String targetPackageName);

    /**
     * Clear all redirection maps for the given theme.
     */
    void clearRedirectionMapsByTheme(in String themePackageName,
            in String themeId);

    /**
     * Clear all redirection maps for the given target package.
     */
    void clearPackageRedirectionMap(in String targetPackageName);
}
