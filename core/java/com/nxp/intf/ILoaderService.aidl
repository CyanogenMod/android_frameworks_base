/*
 * Copyright (C) 2013-2014 NXP Semiconductors
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

package com.nxp.intf;

import android.os.Bundle;
/**
 * {@hide}
 */
interface ILoaderService {

    /* Applet load applet API */
    int appletLoadApplet(in String pkg, in String choice);
    int getListofApplets(in String pkg, out String[] name);
    byte[] getKeyCertificate();
    byte[] lsExecuteScript(in String srcIn, in String rspOut);
    byte[] lsGetVersion();
}
