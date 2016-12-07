/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Cleans up {@link RecentsProvider} and {@link LocalPreferences} when packages are removed.
 */
public class PackageReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final ContentResolver resolver = context.getContentResolver();

        final String action = intent.getAction();
        final Uri data = intent.getData();
        final String packageName = data == null ? null : data.getSchemeSpecificPart();

        if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
            resolver.call(RecentsProvider.buildRecent(), RecentsProvider.METHOD_PURGE, null, null);
            if (packageName != null) {
                LocalPreferences.clearPackagePreferences(context, packageName);
            }
        } else if (Intent.ACTION_PACKAGE_DATA_CLEARED.equals(action)) {
            if (packageName != null) {
                resolver.call(RecentsProvider.buildRecent(), RecentsProvider.METHOD_PURGE_PACKAGE,
                        packageName, null);
                LocalPreferences.clearPackagePreferences(context, packageName);
            }
        }
    }
}
