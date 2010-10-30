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

import android.util.Log;
import android.util.Xml;
import android.os.Environment;

import com.android.internal.util.XmlUtils;
import com.android.internal.telephony.OperatorInfo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * OperatorManager loads the operators.xml file which defines operator information.
 *
 * @hide
 */
public final class OperatorManager {

    private static final String LOG_TAG = "Operator";
    private static final String OPERATORS_FILE = "etc/operators.xml";

    private boolean isOpsFileLoaded;
    private ArrayList<OperatorInfo> mOperators;

    public OperatorManager() {
        this.mOperators = null;
        this.isOpsFileLoaded = false;
        this.mOperators = null;

        loadOperatorFileFromXml();
    }

    private void addOperator(OperatorInfo op) {
        if (op == null) {
            return;
        }

        if (mOperators == null) {
            mOperators = new ArrayList<OperatorInfo>();
        }

        mOperators.add(op);
    }

    /**
     * Load and parse the operators file
     *
     */
    private void loadOperatorFileFromXml() {
        File opsFile = new File(Environment.getRootDirectory(), OPERATORS_FILE);
        FileReader opsReader = null;
        XmlPullParser parser = null;

        try {
            opsReader = new FileReader(opsFile);
            parser = Xml.newPullParser();
            parser.setInput(opsReader);
        } catch (FileNotFoundException e) {
            parser = null;
        } catch (XmlPullParserException e) {
            parser = null;
        }

        if (parser == null) {
            Log.e(LOG_TAG, "can't open operator file: " + opsFile);
            return;
        }

        OperatorInfo op = null;

        try {
            XmlUtils.beginDocument(parser, "Operators");

            while(true) {
                XmlUtils.nextElement(parser);
                String name = parser.getName();

                if (name == null) {
                    break;
                } else if (name.equals("Operator")) {
                    op = new OperatorInfo();
                    op.Name = parser.getAttributeValue(null, "Name");
                    op.ResName = parser.getAttributeValue(null, "Id");
                    addOperator(op);
                } else if (name.equals("Numberic")) {
                    parser.next(); // walk to numberic text 
                    String num = parser.getText();
                    if (op != null) {
                        op.addNumberic(num);
                    }
                }
            }
            isOpsFileLoaded = true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Got exception while loading operator file.", e);
        } finally {
            try {
                if (opsReader != null) {
                    opsReader.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Lookup operator by numberic.
     *
     */
    public OperatorInfo getOperator(String numberic) {
        OperatorInfo op = null;

        if (! isOpsFileLoaded) {
            return null;
        }

        Iterator iter = mOperators.iterator();
        while (iter.hasNext()) {
            op = (OperatorInfo) iter.next();

            if (op.hasNumberic(numberic)) {
                return op;
            }
        }

        return null;
    }
}
