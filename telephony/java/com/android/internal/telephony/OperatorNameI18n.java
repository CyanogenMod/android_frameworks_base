/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.OperatorManager;
import android.content.res.Resources;
import com.android.internal.R;
import android.util.Log;

/**
 * {@hide}
 */
public final class OperatorNameI18n {
    static final String LOG_TAG = "OperatorNameI18n";

    /**
     * Lookup operator name by operator's numberic MCC+MNC
     *
     * @param id operator's numberic
     *
     * @return i18n name of operator
     *
     */
    public static String getName(String id) {
        String name = null;
        Resources ss = null;
        OperatorInfo op = null;
        OperatorManager om = null;

        if (id == null) {
            Log.e(LOG_TAG, "Null value for get carrier name.");
            return name;
        }

        om = new OperatorManager();
        op = om.getOperator(id);
        if (op == null) {
            Log.e(LOG_TAG, "Can't find operator info for " + id);
            return name;
        }

        name = op.Name;

        ss = Resources.getSystem();

        //FIXME: get package name for 3rd arg
        int resId = ss.getIdentifier(op.ResName, "string", "android");

        if (resId == 0) {
            Log.e(LOG_TAG, "Can't find res for operator name " + op.Name);
            return name;
        }

        name = ss.getString(resId);

        Log.d(LOG_TAG, "Operator i18n name for: " + id + " : " + name);

        return name;
    }
}


