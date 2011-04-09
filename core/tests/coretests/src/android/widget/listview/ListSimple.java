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

package android.widget.listview;

import android.util.ListScenario;

import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.widget.TextView;

public class ListSimple extends ListScenario {
    @Override
    protected void init(Params params) {
        params.setStackFromBottom(false)
                .setStartingSelectionPosition(-1)
                .setNumItems(1000)
                .setItemScreenSizeFactor(0.14);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getListView().setVerticalScrollBarEnabled(true);
        getListView().setFadingEdgeLength(12);
        getListView().setVerticalFadingEdgeEnabled(true);
    }

    @Override
    protected View createView(int position, ViewGroup parent, int desiredHeight) {
        View view = super.createView(position, parent, desiredHeight);
        view.setBackgroundColor(0xFF191919);
        ((TextView) view).setTextSize(16.0f);
        return view;
    }
}
