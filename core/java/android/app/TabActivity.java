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

package android.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

/**
 * An activity that contains and runs multiple embedded activities or views.
 */
public class TabActivity extends ActivityGroup {
    private TabHost mTabHost;
    private String mDefaultTab = null;
    private int mDefaultTabIndex = -1;

    public TabActivity() {
    }

    /**
     * Sets the default tab that is the first tab highlighted.
     *
     * @param tag the name of the default tab
     */
    public void setDefaultTab(String tag) {
        mDefaultTab = tag;
        mDefaultTabIndex = -1;
    }

    /**
     * Sets the default tab that is the first tab highlighted.
     *
     * @param index the index of the default tab
     */
    public void setDefaultTab(int index) {
        mDefaultTab = null;
        mDefaultTabIndex = index;
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        ensureTabHost();
        String cur = state.getString("currentTab");
        if (cur != null) {
            mTabHost.setCurrentTabByTag(cur);
        }
        if (mTabHost.getCurrentTab() < 0) {
            if (mDefaultTab != null) {
                mTabHost.setCurrentTabByTag(mDefaultTab);
            } else if (mDefaultTabIndex >= 0) {
                mTabHost.setCurrentTab(mDefaultTabIndex);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle icicle) {
        super.onPostCreate(icicle);

        ensureTabHost();

        if (mTabHost.getCurrentTab() == -1) {
            mTabHost.setCurrentTab(0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String currentTabTag = mTabHost.getCurrentTabTag();
        if (currentTabTag != null) {
            outState.putString("currentTab", currentTabTag);
        }
    }

    /**
     * Updates the screen state (current list and other views) when the
     * content changes.
     *
     *@see Activity#onContentChanged()
     */
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        mTabHost = (TabHost) findViewById(com.android.internal.R.id.tabhost);

        if (mTabHost == null) {
            throw new RuntimeException(
                    "Your content must have a TabHost whose id attribute is " +
                    "'android.R.id.tabhost'");
        }
        mTabHost.setup(getLocalActivityManager());
    }

    private void ensureTabHost() {
        if (mTabHost == null) {
            this.setContentView(com.android.internal.R.layout.tab_content);
        }
    }

    @Override
    protected void
    onChildTitleChanged(Activity childActivity, CharSequence title) {
        // Dorky implementation until we can have multiple activities running.
        if (getLocalActivityManager().getCurrentActivity() == childActivity) {
            View tabView = mTabHost.getCurrentTabView();
            if (tabView != null && tabView instanceof TextView) {
                ((TextView) tabView).setText(title);
            }
        }
    }

    /**
     * Returns the {@link TabHost} the activity is using to host its tabs.
     *
     * @return the {@link TabHost} the activity is using to host its tabs.
     */
    public TabHost getTabHost() {
        ensureTabHost();
        return mTabHost;
    }

    /**
     * Returns the {@link TabWidget} the activity is using to draw the actual tabs.
     *
     * @return the {@link TabWidget} the activity is using to draw the actual tabs.
     */
    public TabWidget getTabWidget() {
        return mTabHost.getTabWidget();
    }
}
