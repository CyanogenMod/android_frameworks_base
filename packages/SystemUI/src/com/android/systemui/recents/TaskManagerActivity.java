/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.systemui.recents;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnGroupClickListener;
import com.android.systemui.statusbar.phone.TaskExpandableListAdapter;

import com.android.systemui.R;

public class TaskManagerActivity extends Activity {
    private ExpandableListView mTaskListView;
    private TaskExpandableListAdapter mTaskListViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.task_manager);
        initTaskListView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTaskListViewAdapter.unregisterReceiver();
    }

    private void initTaskListView() {
        mTaskListView = (ExpandableListView) findViewById(R.id.taskList);
        mTaskListViewAdapter = new TaskExpandableListAdapter(this);
        final TaskListManager taskListManager = new TaskListManager();
        mTaskListViewAdapter.setOnTaskActionListener(taskListManager);
        mTaskListView.setAdapter(mTaskListViewAdapter);
        mTaskListView.setOnGroupClickListener(mOnGroupClickListener);
        mTaskListViewAdapter.expandGroup(null, 0);
        mTaskListViewAdapter.onGroupExpanded(0);
        mTaskListView.expandGroup(0);
        mTaskListView.setGroupIndicator(null);
    }

    private final OnGroupClickListener mOnGroupClickListener = new OnGroupClickListener() {
        public boolean onGroupClick(ExpandableListView parent, View v,
                int groupPosition, long id) {
            return true;
        }
    };

    private class TaskListManager implements TaskExpandableListAdapter.OnTaskActionListener {
        public void onTaskKilled() {
        }
        public void onTaskBroughtToFront() {
        }
    }
}
