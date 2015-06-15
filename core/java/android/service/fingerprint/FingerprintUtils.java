/**
 * Copyright (C) 2014 The Android Open Source Project
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

package android.service.fingerprint;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.fingerprint.Fingerprint;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Utility class for dealing with fingerprints and fingerprint settings.
 * @hide
 */
public
class FingerprintUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = "FingerprintUtils";

    public static List<Fingerprint> getFingerprintsForUser(ContentResolver res, int userId) {
        String fingerprintJson = Settings.Global.getString(res,
                Settings.Global.USER_FINGERPRINTS);
        List<Fingerprint> fingerprints = Fingerprint.JsonSerializer.fromJson(fingerprintJson);

        // Filter out fingerprints that do not match userId
        Iterator<Fingerprint> iter = fingerprints.iterator();
        while(iter.hasNext()) {
            Fingerprint fingerprint = iter.next();
            if (fingerprint.getUserId() != userId) {
                iter.remove();
            }
        }

        return fingerprints;
    }

    public static void addFingerprintIdForUser(int fingerId, Context context,
                                               int userId) {
        // FingerId 0 has special meaning.
        if (fingerId == 0) return;

        // Get existing fingerprints from secure settings
        List<Fingerprint> fingerprints =
                getFingerprintsForUser(context.getContentResolver(), userId);

        // Don't allow dups
        for (Fingerprint fingerprint : fingerprints) {
            if (fingerprint.getFingerId() == fingerId) {
                return;
            }
        }

        // Add the new fingerprint and write back to secure settings
        String defaultName =
                context.getString(com.android.internal.R.string.fingerprint_default_name, fingerId);
        Fingerprint fingerprint = new Fingerprint(defaultName, fingerId, userId);
        fingerprints.add(fingerprint);
        saveFingerprints(fingerprints, context.getContentResolver(), userId);
    }

    public static boolean removeFingerprintIdForUser(int fingerId, ContentResolver res, int userId)
    {
        // FingerId 0 has special meaning. The HAL layer is supposed to remove each finger one
        // at a time and invoke notify() for each fingerId.  If we get called with 0 here, it means
        // something bad has happened.
        if (fingerId == 0) throw new IllegalStateException("Bad fingerId");

        List<Fingerprint> fingerprints = getFingerprintsForUser(res, userId);

        Iterator<Fingerprint> iter = fingerprints.iterator();
        while(iter.hasNext()) {
            if (iter.next().getFingerId() == fingerId) {
                iter.remove();
            }
        }

        saveFingerprints(fingerprints, res, userId);
        return true;
    }



    public static void setFingerprintName(int fingerId, String name,
                                          ContentResolver res, int userId) {
        // FingerId 0 has special meaning. The HAL layer is supposed to remove each finger one
        // at a time and invoke notify() for each fingerId.  If we get called with 0 here, it means
        // something bad has happened.
        if (fingerId == 0) throw new IllegalStateException("Bad fingerId");

        // Find & Replace old fingerprint with newly named fingerprint in the list
        List<Fingerprint> fingerprints = getFingerprintsForUser(res, userId);
        ListIterator<Fingerprint> iter = fingerprints.listIterator();
        while(iter.hasNext()) {
            Fingerprint fingerprint = iter.next();
            if (fingerprint.getFingerId() == fingerId) {
                Fingerprint.Builder builder = new Fingerprint.Builder(fingerprint);
                builder.name(name);
                iter.set(builder.build());
            }
        }

        saveFingerprints(fingerprints, res, userId);
    }


    public static void saveFingerprints(List<Fingerprint> fingerprints,
                                         ContentResolver res, int userId) {
        String json = Fingerprint.JsonSerializer.toJson(fingerprints);
        Settings.Global.putString(res, Settings.Global.USER_FINGERPRINTS, json);
    }
}

