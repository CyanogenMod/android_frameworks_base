/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
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

package com.android.internal.view.menu;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

/**
 * This class was partially copied from com.tmobile.widget.Utils.  Copied here
 * to avoid a bizarre framework dependency on a platform library provided by
 * T-Mobile.
 */
public class Utils {
    /**
     * Alternative to {@link #resolveDefaultStyleAttr(Context, String)} which
     * allows you to specify a resource id for fallback. This is merely an
     * optimization which avoids by name lookup in the current application
     * package scope.
     *
     * @param context
     * @param attrName Attribute name in the currently applied theme.
     * @param fallbackAttrId Attribute id to return if the currently applied
     *            theme does not specify the supplied <code>attrName</code>.
     * @see #resolveDefaultStyleAttr(Context, String)
     */
    public static int resolveDefaultStyleAttr(Context context, String attrName,
            int fallbackAttrId) {
        /* First try to resolve in the currently applied global theme. */
        int attrId = getThemeStyleAttr(context, attrName);
        if (attrId != 0) {
            return attrId;
        }
        /* Fallback to the provided value. */
        return fallbackAttrId;
    }

    /**
     * Dynamically resolve the supplied attribute name within the theme or
     * application scope. First looks at the currently applied global theme,
     * then fallbacks to the current application package.
     *
     * @param context
     * @param attrName Attribute name in the currently applied theme.
     * @return the attribute id suitable for passing to a View's constructor or
     *         0 if neither are provided.
     * @see View#View(Context, android.util.AttributeSet, int)
     */
    public static int resolveDefaultStyleAttr(Context context, String attrName) {
        /* First try to resolve in the currently applied global theme. */
        int attrId = resolveDefaultStyleAttr(context, attrName, 0);
        if (attrId != 0) {
            return attrId;
        }
        /* Then try to lookup in the application's package. */
        return context.getResources().getIdentifier(attrName, "attr",
                context.getPackageName());
    }

    private static int getThemeStyleAttr(Context context, String attrName) {
        String themePackage = getCurrentThemePackage(context);
        if (themePackage == null) {
            return 0;
        }
        return context.getResources().getIdentifier(attrName, "attr", themePackage);
    }

    private static String getCurrentThemePackage(Context context) {
        String themePackage = context.getResources().getAssets().getThemePackageName();
        if (TextUtils.isEmpty(themePackage)) {
            return null;
        }
        return themePackage;
    }
}
