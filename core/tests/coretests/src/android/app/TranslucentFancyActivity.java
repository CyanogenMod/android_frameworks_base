/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.app;

import com.android.frameworks.coretests.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;


/**
 * <h3>Fancy Translucent Activity</h3>
 *
 * <p>This demonstrates the how to write an activity that is translucent,
 * allowing windows underneath to show through, with a fancy
 * compositing effect.</p>
 *
 * <h4>Demo</h4>
 * App/Activity/Translucent Fancy
 *
 * <h4>Source files</h4>
 * <table class="LinkTable">
 *         <tr>
 *             <td >src/com/android/samples/app/TranslucentFancyActivity.java</td>
 *             <td >The Translucent Fancy Screen implementation</td>
 *         </tr>
 *         <tr>
 *             <td >/res/any/layout/translucent_background.xml</td>
 *             <td >Defines contents of the screen</td>
 *         </tr>
 * </table>
 */
public class TranslucentFancyActivity extends Activity
{
    /**
     * Initialization of the Activity after it is first created.  Must at least
     * call {@link android.app.Activity#setContentView setContentView()} to
     * describe what is to be displayed in the screen.
     */
    @Override
    protected void onCreate(Bundle icicle)
    {
        // Be sure to call the super class.
        super.onCreate(icicle);

        // Have the system blur any windows behind this one.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

        // See assets/res/any/layout/translucent_background.xml for this
        // view layout definition, which is being set here as
        // the content of our screen.
        setContentView(R.layout.translucent_background);
    }
}
