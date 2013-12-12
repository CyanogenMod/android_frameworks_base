/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.app.LauncherActivity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ListView;

import com.android.systemui.R;

public class CreateShortcut extends LauncherActivity {

    @Override
    protected Intent getTargetIntent() {
        Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
        targetIntent.addCategory("com.android.systemui.SHORTCUT");
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return targetIntent;
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        Intent shortcutIntent = intentForPosition(position);

        String intentClass = shortcutIntent.getComponent().getClassName();
        String intentAction = shortcutIntent.getAction();

        shortcutIntent = new Intent();
        shortcutIntent.setClassName(this, intentClass);
        shortcutIntent.setAction(intentAction);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                BitmapFactory.decodeResource(getResources(), returnIconResId(intentClass)));
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, itemForPosition(position).label);
        setResult(RESULT_OK, intent);
        finish();
    }

    private int returnIconResId(String intentClass) {
        String c = intentClass.substring(intentClass.lastIndexOf(".") + 1);

        if (c.equals ("Immersive")) {
            return R.drawable.ic_qs_expanded_desktop_on;
        } else if (c.equals ("QuietHours")) {
            return R.drawable.ic_qs_quiet_hours_on;
        } else if (c.equals("Rotation")) {
            return R.drawable.ic_qs_auto_rotate;
        } else if (c.equals("Torch")) {
            return R.drawable.ic_qs_torch_on;
        } else if (c.equals("LastApp")) {
            return R.drawable.ic_sysbar_lastapp;
        } else if (c.equals("Reboot")) {
            return R.drawable.ic_qs_reboot;
        } else if (c.equals("Screenshot")) {
            return R.drawable.ic_sysbar_screenshot;
        } else {
            // Oh-Noes, you found a wild derp.
            return R.drawable.ic_sysbar_null;
        }
    }
}
