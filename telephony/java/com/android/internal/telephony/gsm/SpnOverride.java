/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Environment;
import android.util.Log;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

public class SpnOverride {
    private HashMap<String, HashMap<String, String>> mCarrierLocaleSpnMap;


    static final String LOG_TAG = "GSM";
    static final String PARTNER_SPN_OVERRIDE_PATH ="etc/spn-conf.xml";

    SpnOverride () {
        mCarrierLocaleSpnMap = new HashMap<String, HashMap<String, String>>();
        loadSpnOverrides();
    }

    boolean containsCarrier(String carrier) {
        return mCarrierLocaleSpnMap.containsKey(carrier);
    }

    String getSpn(String carrier) {
        return getSpn(carrier, Locale.ROOT);
    }

    String getSpn(String carrier, Locale locale) {
        HashMap<String, String> localeSpnMap;
        String localeKey;

        if (carrier == null || locale == null || !containsCarrier(carrier) ||
                (localeSpnMap = mCarrierLocaleSpnMap.get(carrier)) == null ||
                !localeSpnMap.containsKey(localeKey = locale.toString()) &&
                !localeSpnMap.containsKey(localeKey = locale.getLanguage())) {
            return null;
        }

        return localeSpnMap.get(localeKey);
    }

    private void loadSpnOverrides() {
        FileReader spnReader;

        final File spnFile = new File(Environment.getRootDirectory(),
                PARTNER_SPN_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_SPN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "spnOverrides");
            XmlUtils.nextElement(parser);

            while (true) {
                String name = parser.getName();
                if (!"spnOverride".equals(name)) {
                    break;
                }

                String numeric = parser.getAttributeValue(null, "numeric");

                if (numeric == null || "".equals(numeric)) {
                    XmlUtils.nextElement(parser);
                    while ("spn".equals(parser.getName())) {
                        XmlUtils.nextElement(parser);
                    }
                } else {
                    HashMap<String, String> localeSpnMap = new HashMap<String, String>();
                    localeSpnMap.put("", parser.getAttributeValue(null, "spn"));

                    while (true) {
                        XmlUtils.nextElement(parser);

                        if (!"spn".equals(parser.getName())) {
                            break;
                        }

                        String locale = parser.getAttributeValue(null, "locale");
                        String spn = parser.nextText();

                        if (locale != null && !"".equals(locale)) {
                            localeSpnMap.put(locale, spn);
                        }
                    }

                    mCarrierLocaleSpnMap.put(numeric, localeSpnMap);
                }
            }
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in spn-conf parser " + e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Exception in spn-conf parser " + e);
        }
    }

}
