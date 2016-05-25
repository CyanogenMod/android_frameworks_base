/*
 *
 *  Copyright (C) 2015 NXP Semiconductors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.gsma.services.nfc;

import android.content.Context;
import java.io.IOException;

/**
 * This class handles the available Secure Elements
 * @since NFCHST4.1 <I>(REQ_126)</I>
 * @deprecated <a style="color:#FF0000">When Host Card Emulation (HCE) is supported</a>
 */
@Deprecated
public class SEController {

    SEController() {}


    // Callback interface

    /**
     * This interface provide callback methods for {@link SEController} class
     * @since NFCHST4.1
     * @deprecated <a style="color:#FF0000">When Host Card Emulation (HCE) is supported</a>
     */
    public static interface Callbacks {

        /**
         * Called when process for getting the default Controller is finished.
         * @param controller Instance of default controller or <code>null</code> if an error occurred
         * @since NFCHST4.1
         */
        public abstract void onGetDefaultController(SEController controller);

    }


    // Handling the SE Controller

    /**
     * Helper for getting an instance of the SE Controller.
     * @param context Calling application's context
     * @param cb Callback interface
     * @since NFCHST4.1
     * @deprecated .
     * @throws IOException
     */
    @Deprecated
    public static void getDefaultController(Context context, SEController.Callbacks cb) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Return the name of the active Secure Element.
     * @return Name of the active Secure Element
     * @since NFCHST4.1 <I>(REQ_126)</I>
     * @deprecated .
     * @throws IOException
     */
    @Deprecated
    public String getActiveSecureElement() {
        throw new UnsupportedOperationException();
    }

    /**
     * Set a specified Secure Element as "active" one.
     * @param SEName Secure Element name
     * @exception IllegalStateException <BR>Indicate that NFC Controller is not enabled.
     * @exception SecurityException <BR>Indicate that application SHALL be signed with a trusted certificate for using this API.
     * @since NFCHST4.1 <I>(REQ_126)</I>
     * @deprecated .
     * @throws IOException
     */
    @Deprecated
    public void setActiveSecureElement(String SEName) {
        throw new UnsupportedOperationException();
    }

}
