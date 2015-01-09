/*
 * Copyright (C) 2015 The New One Android
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

package android.one.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.Locale;

/**
* @hide
*/

public class OneUtils {

    public static boolean isSupportLanguage(boolean excludeTW) {
        Configuration configuration = Resources.getSystem().getConfiguration();
        if (excludeTW) {
            return configuration.locale.getLanguage().startsWith(Locale.CHINESE.getLanguage())
                    && !configuration.locale.getCountry().equals("TW");
        } else {
            return configuration.locale.getLanguage().startsWith(Locale.CHINESE.getLanguage());
        }
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }

}

