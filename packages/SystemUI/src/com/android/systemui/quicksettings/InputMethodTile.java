/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.util.List;

public class InputMethodTile extends QuickSettingsTile {
    private InputMethodManager mImm;
    private boolean mShowTile = false;

    private static final String TAG_TRY_SUPPRESSING_IME_SWITCHER = "TrySuppressingImeSwitcher";

    public InputMethodTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mImm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);

                Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                try {
                    pendingIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    // ignore, nothing we can do about it
                }
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(mShowTile ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    public void toggleVisibility(boolean show) {
        mShowTile = show && needsToShowImeSwitchOngoingNotification();
        updateResources();
    }

    private void updateTile() {
        mLabel = getCurrentInputMethodName();
        mDrawable = R.drawable.ic_qs_ime;
    }

    private String getCurrentInputMethodName() {
        List<InputMethodInfo> imis = mImm.getInputMethodList();
        if (imis == null) {
            return null;
        }

        final String currentInputMethodId = Settings.Secure.getStringForUser(
                mContext.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD,
                UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(currentInputMethodId)) {
            return null;
        }

        for (InputMethodInfo imi : imis) {
            if (!currentInputMethodId.equals(imi.getId())) {
                continue;
            }
            final InputMethodSubtype subtype = mImm.getCurrentInputMethodSubtype();
            if (subtype == null) {
                return mContext.getString(R.string.quick_settings_ime_label);
            }

            return subtype.getDisplayName(mContext, imi.getPackageName(),
                    imi.getServiceInfo().applicationInfo).toString();
        }
        return null;
    }

    private boolean needsToShowImeSwitchOngoingNotification() {
        List<InputMethodInfo> imis = mImm.getEnabledInputMethodList();
        final int N = imis.size();

        if (N > 2) {
            return true;
        }
        if (N < 1) {
            return false;
        }

        int nonAuxCount = 0;
        int auxCount = 0;
        InputMethodSubtype nonAuxSubtype = null;
        InputMethodSubtype auxSubtype = null;

        for(int i = 0; i < N; ++i) {
            final InputMethodInfo imi = imis.get(i);
            final List<InputMethodSubtype> subtypes =
                    mImm.getEnabledInputMethodSubtypeList(imi, true);
            final int subtypeCount = subtypes.size();
            if (subtypeCount == 0) {
                ++nonAuxCount;
            } else {
                for (int j = 0; j < subtypeCount; ++j) {
                    final InputMethodSubtype subtype = subtypes.get(j);
                    if (!subtype.isAuxiliary()) {
                        ++nonAuxCount;
                        nonAuxSubtype = subtype;
                    } else {
                        ++auxCount;
                        auxSubtype = subtype;
                    }
                }
            }
        }
        if (nonAuxCount > 1 || auxCount > 1) {
            return true;
        } else if (nonAuxCount == 1 && auxCount == 1) {
            if (nonAuxSubtype != null && auxSubtype != null
                    && (nonAuxSubtype.getLocale().equals(auxSubtype.getLocale())
                            || auxSubtype.overridesImplicitlyEnabledSubtype()
                            || nonAuxSubtype.overridesImplicitlyEnabledSubtype())
                    && nonAuxSubtype.containsExtraValueKey(TAG_TRY_SUPPRESSING_IME_SWITCHER)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
