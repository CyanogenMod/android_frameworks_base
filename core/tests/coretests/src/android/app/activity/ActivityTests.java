/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.app.activity;

import junit.framework.TestSuite;

public class ActivityTests {
    public static final boolean DEBUG_LIFECYCLE = false;

    public static TestSuite suite() {
        TestSuite suite = new TestSuite(ActivityTests.class.getName());

        suite.addTestSuite(BroadcastTest.class);
        suite.addTestSuite(IntentSenderTest.class);
        suite.addTestSuite(ActivityManagerTest.class);
        suite.addTestSuite(LaunchTest.class);
        suite.addTestSuite(LifecycleTest.class);
        suite.addTestSuite(ServiceTest.class);
        suite.addTestSuite(MetaDataTest.class);
        // Remove temporarily until bug 1171309 is fixed.
        //suite.addTestSuite(SubActivityTest.class);
        suite.addTestSuite(SetTimeZonePermissionsTest.class);

        return suite;
    }
}
