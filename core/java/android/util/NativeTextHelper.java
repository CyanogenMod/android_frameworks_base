/*
 * Copyright (C) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package android.util;

import android.content.Context;

/**
 *@hide
 */
public class NativeTextHelper {

    /**
     * parse the string to current language.
     *
     * @param context base context of the application
     * @param originalString original string
     * @param defPackage the target package where the local language strings
     *            defined
     * @param originNamesId the id of the original string array.
     * @param localNamesId the id of the local string keys.
     * @return local language string
     */
    private static final String getLocalString(Context context, String originalString,
            String defPackage, int originNamesId, int localNamesId) {
        String[] origNames = context.getResources().getStringArray(originNamesId);
        String[] localNames = context.getResources().getStringArray(localNamesId);
        for (int i = 0; i < origNames.length; i++) {
            if (origNames[i].equalsIgnoreCase(originalString)) {
                return context.getString(context.getResources().getIdentifier(localNames[i],
                        "string", defPackage));
            }
        }
        return originalString;
    }

    /**
     * parse the string to current language string in public resources.
     *
     * @param context base context of the application
     * @param originalString original string
     * @param originNamesId the id of the original string array.
     * @param localNamesId the id of the local string keys.
     * @return local language string
     */
    public static final String getLocalString(Context context, String originalString,
            int originNamesId, int localNamesId) {
        return getLocalString(context, originalString, "android", originNamesId, localNamesId);
    }

    /**
     * parse the string to current language string in current resources.
     *
     * @param context base context of the application
     * @param originalString original string
     * @param originNamesId the id of the original string array.
     * @param localNamesId the id of the local string keys.
     * @return local language string
     */
    public static final String getInternalLocalString(Context context, String originalString,
            int originNamesId,
            int localNamesId) {
        return getLocalString(context, originalString, context.getPackageName(), originNamesId,
                localNamesId);
    }

}
