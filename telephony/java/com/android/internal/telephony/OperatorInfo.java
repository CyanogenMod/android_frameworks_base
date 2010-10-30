/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/*
 * @hide
 */
public final class OperatorInfo {

    public String Name;
    public String ResName;  // Resource name for the Name
    public List<String> Numberics;

    public OperatorInfo () {
        this.Name = null;
        this.Numberics = null;
        this.ResName = null;
    }

    public void addNumberic(String numberic) {
        if (numberic == null) {
            return;
        }

        if (Numberics == null) {
            Numberics = new ArrayList<String>();
        }
        else
        {
            if (Numberics.contains(numberic)) {
                return;
            }
        }

        Numberics.add(numberic);
    }

    public boolean hasNumberic(String numberic) {
        if (numberic == null || Numberics == null) {
            return false;
        }

        return Numberics.contains(numberic);
    }

    public String toString() {
        String string = "Operator: " + Name + ", " + ResName;

        if (Numberics == null) {
            return string;
        }

        Iterator iter = Numberics.iterator();
        while (iter.hasNext()) {
            string += ", " + (String) iter.next();
        }

        return string;
    }
}
