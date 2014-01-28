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

package com.android.imftest.samples;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;

import com.android.internal.R;

/*
 * Activity with non-EditText view selected initially
 */
public class OneEditTextActivityNotSelected extends Activity
{
    private View mRootView;
    private View mDefaultFocusedView;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        mRootView = new ScrollView(this);

        EditText editText = new EditText(this);
        Button button = new Button(this);
        button.setText("The focus is here.");
        button.setFocusableInTouchMode(true);
        button.requestFocus();
        mDefaultFocusedView = button;
        layout.addView(button);
        layout.addView(editText);

        ((ScrollView) mRootView).addView(layout);
        setContentView(mRootView);
    }

    public View getRootView() {
        return mRootView;
    }

    public View getDefaultFocusedView() {
        return mDefaultFocusedView;
    }
}
