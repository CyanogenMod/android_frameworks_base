/*
 * Copyright (C) 2008 The Android Open Source Project
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


package com.android.tools.layoutlib.create;


import static org.junit.Assert.assertArrayEquals;

import com.android.tools.layoutlib.create.LogTest.MockLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

/**
 * Unit tests for some methods of {@link AsmGenerator}.
 */
public class AsmGeneratorTest {

    private MockLog mLog;
    private ArrayList<String> mOsJarPath;
    private String mOsDestJar;
    private File mTempFile;

    @Before
    public void setUp() throws Exception {
        mLog = new LogTest.MockLog();
        URL url = this.getClass().getClassLoader().getResource("data/mock_android.jar");

        mOsJarPath = new ArrayList<String>();
        mOsJarPath.add(url.getFile());

        mTempFile = File.createTempFile("mock", "jar");
        mOsDestJar = mTempFile.getAbsolutePath();
        mTempFile.deleteOnExit();
    }

    @After
    public void tearDown() throws Exception {
        if (mTempFile != null) {
            mTempFile.delete();
            mTempFile = null;
        }
    }

    @Test
    public void testClassRenaming() throws IOException, LogAbortException {

        AsmGenerator agen = new AsmGenerator(mLog, mOsDestJar,
            null, // classes to inject in the final JAR
            null,  // methods to force override
            new String[] {  // classes to rename (so that we can replace them)
                "mock_android.view.View", "mock_android.view._Original_View",
                "not.an.actual.ClassName", "anoter.fake.NewClassName",
            },
            null // methods deleted from their return type.
            );

        AsmAnalyzer aa = new AsmAnalyzer(mLog, mOsJarPath, agen,
                null,                 // derived from
                new String[] {        // include classes
                    "**"
                });
        aa.analyze();
        agen.generate();

        Set<String> notRenamed = agen.getClassesNotRenamed();
        assertArrayEquals(new String[] { "not/an/actual/ClassName" }, notRenamed.toArray());
    }
}
