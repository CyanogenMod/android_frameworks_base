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

package com.android.internal.util.nameless.constants;

import android.content.Intent;

public class FlashLightConstants {

    /**
     * Package name of the flashlight app
     */
    public static final String APP_PACKAGE_NAME = "net.cactii.flash2";

     /**
     * Service name of the flashlight app
     */
    public static final String APP_SERVICE_NAME = APP_PACKAGE_NAME + ".TorchService";

    /**
     * Intent broadcast action for toggling the torch state
     */
    public static final String ACTION_TOGGLE_STATE = APP_PACKAGE_NAME + ".TOGGLE_FLASHLIGHT";

    /**
     * Intent broadcast action for turning the torch off
     */
    public static final String ACTION_OFF = APP_PACKAGE_NAME + ".FLASHLIGHT_OFF";

    /**
     * Intent broadcast action for turning the torch on
     */
    public static final String ACTION_ON = APP_PACKAGE_NAME + ".FLASHLIGHT_ON";

    /**
     * Intent action for 'flash state changed' broadcast
     */
    public static final String ACTION_STATE_CHANGED = APP_PACKAGE_NAME + ".FLASHLIGHT_STATUS_CHANGED";

    /**
     * Extra for {@link ACTION_STATUS_CHANGED}:
     * Current torch state
     * Type: integer (0/1)
     */
    public static final String EXTRA_CURRENT_STATE = "FLASHLIGHT_MODE";

    /**
     * Intent for launching the torch application
     */
    public static final Intent INTENT_LAUNCH_APP = new Intent(Intent.ACTION_MAIN)
            .setClassName(APP_PACKAGE_NAME, APP_PACKAGE_NAME + ".MainActivity");
}
