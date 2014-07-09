/*
* <!--
*    Copyright (C) 2014 The NamelessROM Project
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
* -->
*/

package com.android.internal.util.nameless;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class NamelessActions {

    public static final String ACTION_ONTHEGO_TOGGLE = "action_onthego_toggle";

    public static void processAction(final Context context, final String action) {

        if (action == null || action.isEmpty()) {
            return;
        }

        if (ACTION_ONTHEGO_TOGGLE.equals(action)) {
            actionOnTheGoToggle(context);
        }

    }

    private static void actionOnTheGoToggle(final Context context) {
        final ComponentName cn = new ComponentName("com.android.systemui",
                "com.android.systemui.nameless.onthego.OnTheGoService");
        final Intent startIntent = new Intent();
        startIntent.setComponent(cn);
        startIntent.setAction("start");
        context.startService(startIntent);
    }

}
