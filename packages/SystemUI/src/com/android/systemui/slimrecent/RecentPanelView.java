/*
 * Copyright (C) 2014 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.slimrecent;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.View;
import android.widget.ImageView;

import com.android.cards.internal.Card;
import com.android.cards.internal.CardArrayAdapter;
import com.android.cards.view.CardListView;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Our main view controller which handles and construct most of the view
 * related tasks.
 *
 * Constructing the actual cards, add the listeners, loading or updating the tasks
 * and inform all relevant classes with the listeners is done here.
 *
 * As well the actual click, longpress or swipe action methods are holded here.
 */
public class RecentPanelView {

    private static final String TAG = "RecentPanelView";
    private static final boolean DEBUG = false;

    private static final int DISPLAY_TASKS = 20;
    public static final int MAX_TASKS = DISPLAY_TASKS + 1; // allow extra for non-apps

    private static final int EXPANDED_STATE_UNKNOWN   = 0;
    public static final int EXPANDED_STATE_EXPANDED  = 1;
    public static final int EXPANDED_STATE_COLLAPSED = 2;
    public static final int EXPANDED_STATE_BY_SYSTEM = 4;

    private final Context mContext;
    private final CardListView mListView;
    private final ImageView mEmptyRecentView;

    private final RecentController mController;

    // Our array adapter holding all cards
    private CardArrayAdapter mCardArrayAdapter;
    // Array list of all current cards
    private ArrayList<Card> mCards;
    // Array list of all current tasks
    private final ArrayList<TaskDescription> mTasks = new ArrayList<TaskDescription>();;
    // Array list of all expanded states of apps accessed during the session
    private final ArrayList<TaskExpandedStates> mExpandedTaskStates =
            new ArrayList<TaskExpandedStates>();

    private boolean mCancelledByUser;
    private boolean mTasksLoaded;
    private boolean mIsLoading;
    private int mTasksSize;

    public interface OnExitListener {
        void onExit();
    }
    private OnExitListener mOnExitListener = null;

    public void setOnExitListener(OnExitListener onExitListener) {
        mOnExitListener = onExitListener;
    }

    public interface OnTasksLoadedListener {
        void onTasksLoaded();
    }
    private OnTasksLoadedListener mOnTasksLoadedListener = null;

    public void setOnTasksLoadedListener(OnTasksLoadedListener onTasksLoadedListener) {
        mOnTasksLoadedListener = onTasksLoadedListener;
    }

    public RecentPanelView(Context context, RecentController controller,
            CardListView listView, ImageView emptyRecentView) {
        mContext = context;
        mListView = listView;
        mEmptyRecentView = emptyRecentView;
        mController = controller;

        buildCardListAndAdapter();
    }

    /**
     * Build card list and arrayadapter we need to fill with tasks
     */
    protected void buildCardListAndAdapter() {
        mCards = new ArrayList<Card>();
        mCardArrayAdapter = new CardArrayAdapter(mContext, mCards);
        if (mListView != null) {
            mListView.setAdapter(mCardArrayAdapter);
        }
    }

    /**
     * Assign the listeners to the card.
     */
    private RecentCard assignListeners(final RecentCard card, final TaskDescription td) {
        if (DEBUG) Log.v(TAG, "add listeners to task card");

        // Listen for swipe to close and remove the app.
        card.setSwipeable(true);
        card.setOnSwipeListener(new Card.OnSwipeListener() {
            @Override
            public void onSwipe(Card card) {
                removeApplication(td);
            }
        });
        // Listen for onClick to start the app with custom animation
        card.setOnClickListener(new Card.OnCardClickListener() {
            @Override
            public void onClick(Card card, View view) {
                startApplication(td);
            }
        });
        // Listen for onLongClick to open app details with custom animation
        card.setOnLongClickListener(new Card.OnLongCardClickListener() {
            @Override
            public boolean onLongClick(Card card, View view) {
                startApplicationDetailsActivity(td.packageName);
                return true;
            }
        });
        // Listen for card is expanded to save current value for next recent call
        card.setOnExpandAnimatorEndListener(new Card.OnExpandAnimatorEndListener() {
            @Override
            public void onExpandEnd(Card card) {
                if (DEBUG) Log.v(TAG, td.getLabel() + " is expanded");
                final int oldState = td.getExpandedState();
                int state = EXPANDED_STATE_EXPANDED;
                if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                    state |= EXPANDED_STATE_BY_SYSTEM;
                }
                td.setExpandedState(state);
            }
        });
        // Listen for card is collapsed to save current value for next recent call
        card.setOnCollapseAnimatorEndListener(new Card.OnCollapseAnimatorEndListener() {
            @Override
            public void onCollapseEnd(Card card) {
                if (DEBUG) Log.v(TAG, td.getLabel() + " is collapsed");
                final int oldState = td.getExpandedState();
                int state = EXPANDED_STATE_COLLAPSED;
                if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                    state |= EXPANDED_STATE_BY_SYSTEM;
                }
                td.setExpandedState(state);
            }
        });
        return card;
    }

    /**
     * Remove requested application.
     */
    private void removeApplication(TaskDescription td) {
        if (DEBUG) Log.v(TAG, "Jettison " + td.getLabel());

        // Kill the actual app and send accessibility event.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.removeTask(td.persistentTaskId, ActivityManager.REMOVE_TASK_KILL_PROCESS);

            // Accessibility feedback
            mListView.setContentDescription(
                    mContext.getString(R.string.accessibility_recents_item_dismissed,
                            td.getLabel()));
            mListView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            mListView.setContentDescription(null);

            // Remove app from task, cache and expanded state list.
            removeApplicationBitmapCacheAndExpandedState(td);
            mTasks.remove(td);
            mTasksSize--;
        }

        // All apps were removed? Close recents panel.
        if (mTasksSize == 0) {
            setVisibility();
            exit();
        }
    }

    /**
     * Remove all applications. Call from controller class
     */
    protected void removeAllApplications() {
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (TaskDescription td : mTasks) {
            // Kill all recent apps.
            if (am != null) {
                am.removeTask(td.persistentTaskId, ActivityManager.REMOVE_TASK_KILL_PROCESS);
                removeApplicationBitmapCacheAndExpandedState(td);
            }
        }
        // Clear all relevant values.
        mTasks.clear();
        mCards.clear();
        mTasksSize = 0;
    }

    /**
     * Remove application bitmaps from LRU cache and expanded state list.
     */
    private void removeApplicationBitmapCacheAndExpandedState(TaskDescription td) {
            // Remove application thumbnail.
            CacheController.getInstance(mContext)
                    .removeBitmapFromMemCache(String.valueOf(td.persistentTaskId));
            // Remove application icon.
            CacheController.getInstance(mContext)
                    .removeBitmapFromMemCache(td.packageName);
            // Remove from expanded state list.
            removeExpandedTaskState(td.getLabel());
    }

    /**
     * Start application or move to forground if still active.
     */
    private void startApplication(TaskDescription td) {
        // Starting app is requested by the user.
        // Move it to foreground or start it with custom animation.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final Bundle opts = ActivityOptions.makeCustomAnimation(
                mContext, com.android.internal.R.anim.recent_screen_enter,
                com.android.internal.R.anim.recent_screen_fade_out).toBundle();
        if (td.taskId >= 0) {
            // This is an active task; it should just go to the foreground.
            am.moveTaskToFront(td.taskId, ActivityManager.MOVE_TASK_WITH_HOME, opts);
        } else {
            final Intent intent = td.intent;
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                    | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (DEBUG) Log.v(TAG, "Starting activity " + intent);
            try {
                mContext.startActivityAsUser(intent, opts,
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (SecurityException e) {
                Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Error launching activity " + intent, e);
            }
        }
        exit();
    }

    /**
     * Start application details screen.
     */
    private void startApplicationDetailsActivity(String packageName) {
        // Starting app details screen is requested by the user.
        // Start it with custom animation.
        final Bundle opts = ActivityOptions.makeCustomAnimation(
                mContext, com.android.internal.R.anim.recent_screen_enter,
                com.android.internal.R.anim.recent_screen_fade_out).toBundle();
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext)
                .addNextIntentWithParentStack(intent).startActivities(opts);
        exit();
    }

    /**
     * Create a TaskDescription, returning null if the title or icon is null.
     */
    private TaskDescription createTaskDescription(int taskId, int persistentTaskId,
            Intent baseIntent, ComponentName origActivity,
            CharSequence description, int expandedState) {

        final Intent intent = new Intent(baseIntent);
        if (origActivity != null) {
            intent.setComponent(origActivity);
        }
        final PackageManager pm = mContext.getPackageManager();
        intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        if (resolveInfo != null) {
            final ActivityInfo info = resolveInfo.activityInfo;
            final String title = info.loadLabel(pm).toString();

            if (title != null && title.length() > 0) {
                if (DEBUG) Log.v(TAG, "creating activity desc for id="
                        + persistentTaskId + ", label=" + title);

                final TaskDescription item = new TaskDescription(taskId,
                        persistentTaskId, resolveInfo, baseIntent, info.packageName,
                        description, expandedState);
                item.setLabel(title);
                return item;
            } else {
                if (DEBUG) Log.v(TAG, "SKIPPING item " + persistentTaskId);
            }
        }
        return null;
    }

    /**
     * Load all tasks we want.
     */
    protected void loadTasks() {
        if (isTasksLoaded() || mIsLoading) {
            return;
        }
        if (DEBUG) Log.v(TAG, "loading tasks");
        mIsLoading = true;
        updateExpandedTaskStates();
        mTasks.clear();

        final PackageManager pm = mContext.getPackageManager();
        final ActivityManager am = (ActivityManager)
        mContext.getSystemService(Context.ACTIVITY_SERVICE);

        final List<ActivityManager.RecentTaskInfo> recentTasks =
                am.getRecentTasksForUser(MAX_TASKS, ActivityManager.RECENT_IGNORE_UNAVAILABLE
                        | ActivityManager.RECENT_WITH_EXCLUDED
                        | ActivityManager.RECENT_DO_NOT_COUNT_EXCLUDED,
                        UserHandle.CURRENT.getIdentifier());
        final int numTasks = recentTasks.size();
        ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(pm, 0);

        int firstItems = 0;
        final int firstExpandedItems =
                mContext.getResources().getInteger(R.integer.expanded_items_default);
        // Get current task list. We do not need to do it in background. We only load MAX_TASKS.
        // Skip the first task - assume it's either the home screen or the current activity.
        final int first = 1;
        for (int i = first, index = 0; i < numTasks && (index < MAX_TASKS); ++i) {
            if (mCancelledByUser) {
                if (DEBUG) Log.v(TAG, "loading tasks cancelled");
                mIsLoading = false;
                return;
            }
            final ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(i);

            final Intent intent = new Intent(recentInfo.baseIntent);
            if (recentInfo.origActivity != null) {
                intent.setComponent(recentInfo.origActivity);
            }

            // Never load the current home activity.
            if (isCurrentHomeActivity(intent.getComponent(), homeInfo)) {
                continue;
            }

            // Don't load excluded activities.
            if ((recentInfo.baseIntent.getFlags()
                    & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0) {
                continue;
            }

            TaskDescription item = createTaskDescription(recentInfo.id,
                    recentInfo.persistentId, recentInfo.baseIntent,
                    recentInfo.origActivity, recentInfo.description, EXPANDED_STATE_UNKNOWN);

            if (item != null) {
                // FirstExpandedItems value forces to show always the app screenshot
                // if the old state is not known.
                // All other items we check if they were expanded from the user
                // in last known recent app list and restore the state.
                int oldState = getExpandedState(item);
                if (DEBUG) Log.v(TAG, "old expanded state = " + oldState);
                if (firstItems < firstExpandedItems) {
                    item.setExpandedState(oldState | EXPANDED_STATE_BY_SYSTEM);
                } else {
                    if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                        oldState &= ~EXPANDED_STATE_BY_SYSTEM;
                    }
                    item.setExpandedState(oldState);
                }
                firstItems++;
                mTasks.add(item);
            }
        }

        mTasksSize = mTasks.size();

        // We have all needed tasks now.
        // Let us load the cards for it in background.
        final CardLoader cardLoader = new CardLoader();
        cardLoader.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Set correct visibility states for the listview and the empty recent icon.
     */
    private void setVisibility() {
        mEmptyRecentView.setVisibility(mTasksSize == 0 ? View.VISIBLE : View.GONE);
        mListView.setVisibility(mTasksSize == 0 ? View.GONE : View.VISIBLE);
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Update the List for actual apps.
     */
    private void updateExpandedTaskStates() {
        for (TaskDescription item : mTasks) {
            boolean updated = false;
            for (TaskExpandedStates expandedState : mExpandedTaskStates) {
                if (item.getLabel().equals(expandedState.getLabel())) {
                    updated = true;
                    expandedState.setExpandedState(item.getExpandedState());
                }
            }
            if (!updated) {
                mExpandedTaskStates.add(
                        new TaskExpandedStates(item.getLabel(), item.getExpandedState()));
            }
        }
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Get expanded state of the app.
     */
    private int getExpandedState(TaskDescription item) {
        for (TaskExpandedStates oldTask : mExpandedTaskStates) {
            if (DEBUG) Log.v(TAG, "old task label = "+ oldTask.getLabel()
                    + " new task label = " + item.getLabel());
            if (item.getLabel().equals(oldTask.getLabel())) {
                    return oldTask.getExpandedState();
            }
        }
        return EXPANDED_STATE_UNKNOWN;
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Remove expanded state entry due that app was removed by the user.
     */
    private void removeExpandedTaskState(String label) {
        TaskExpandedStates expandedStateToDelete = null;
        for (TaskExpandedStates expandedState : mExpandedTaskStates) {
            if (expandedState.getLabel().equals(label)) {
                expandedStateToDelete = expandedState;
            }
        }
        if (expandedStateToDelete != null) {
            mExpandedTaskStates.remove(expandedStateToDelete);
        }
    }

    protected void notifyDataSetChanged(boolean forceupdate) {
        if (forceupdate || !mController.isShowing()) {
            // We want to have the list scrolled down before it is visible for the user.
            // Whoever calls notifyDataSetChanged() first (not visible) do it now.
            if (mListView != null) {
                mListView.setSelection(mCards.size() - 1);
            }
            mCardArrayAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Third loading stage. Container is now visible,
     * tasks were completly loaded, visible elements
     * were loaded as well. So let us trigger for all invisible
     * views the asynctask loaders. This triggers bitmap load
     * for collapsed expanded cards and as well app icon load
     * for all non visible cards on the screen.
     * We are doing this here to avoid peformance issues
     * on scrolling. Recents screen has a max entry of 21
     * tasks so this is a good approach to load now all
     * user information without having any downsides.
     *
     */
    protected void updateInvisibleCards() {
        RecentCard card;
        final int size = mCards.size();
        // We set here an internal value
        // to prepare force load of the task
        // thumbnails.
        for (int i = 0; i < size; i++) {
            card = (RecentCard) mCards.get(i);
            card.forceSetLoadExpandedContent();
        }
        // Actually trigger on all cards the load if
        // the content was not loaded allready. This
        // decisision is done in the cards themselves.
        for (int i = size - 1; i >= 0; i--) {
            mCardArrayAdapter.getView(i, null, mListView);
        }
    }

    protected void setCancelledByUser(boolean cancelled) {
        mCancelledByUser = cancelled;
        if (cancelled) {
            setTasksLoaded(false);
        }
    }

    protected void setTasksLoaded(boolean loaded) {
        mTasksLoaded = loaded;
    }

    protected boolean isCancelledByUser() {
        return mCancelledByUser;
    }

    protected boolean isTasksLoaded() {
        return mTasksLoaded;
    }

    /**
     * Notify listener that tasks are loaded.
     */
    private void tasksLoaded() {
        if (mOnTasksLoadedListener != null) {
            setTasksLoaded(true);
            mIsLoading = false;
            mOnTasksLoadedListener.onTasksLoaded();
        }
    }

    /**
     * Notify listener that we exit recents panel now.
     */
    private void exit() {
        if (mOnExitListener != null) {
            mOnExitListener.onExit();
        }
    }

    private boolean isCurrentHomeActivity(ComponentName component, ActivityInfo homeInfo) {
        if (homeInfo == null) {
            final PackageManager pm = mContext.getPackageManager();
            homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .resolveActivityInfo(pm, 0);
        }
        return homeInfo != null
            && homeInfo.packageName.equals(component.getPackageName())
            && homeInfo.name.equals(component.getClassName());
    }

    /**
     * AsyncTask cardloader to load all cards in background. Preloading
     * forces as well a card load or update. So if the user cancelled the preload
     * or does not even open the recent panel we want to reduce the system
     * load as much as possible. So we do it in background.
     *
     * Note: App icons as well the app screenshots are loaded in other
     *       async tasks.
     *       See #link:RecentCard, #link:RecentExpandedCard
     *       #link:RecentAppIcon and #link AppIconLoader
     */
    private class CardLoader extends AsyncTask<Void, Void, Boolean> {

        private int mOrigPri;
        private int mCounter;

        public CardLoader() {
            // Empty constructor.
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // Save current thread priority and set it during the loading
            // to background priority.
            mOrigPri = Process.getThreadPriority(Process.myTid());
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            int oldSize = mCards.size();
            mCounter = 0;
            // Construct or update cards and publish cards recursive with current tasks.
            for (int i = mTasksSize - 1; i >= 0; i--) {
                if (isCancelled() || mCancelledByUser) {
                    if (DEBUG) Log.v(TAG, "loading tasks cancelled");
                    return false;
                }

                final TaskDescription task = mTasks.get(i);
                RecentCard card = null;

                // We may have allready constructed and inflated card.
                // Let us reuse them and just update the content.
                if (mCounter < oldSize) {
                    card = (RecentCard) mCards.get(mCounter);
                    if (card != null) {
                        if (DEBUG) Log.v(TAG, "loading tasks - update old card");
                        card.updateCardContent(task);
                        card = assignListeners(card, task);
                    }
                }

                // No old card was present to update....so add a new one.
                if (card == null) {
                    if (DEBUG) Log.v(TAG, "loading tasks - create new card");
                    card = new RecentCard(mContext, task);
                    card = assignListeners(card, task);
                    mCards.add(card);
                }

                mCounter++;
            }

            // We may have unused cards left. Eg app was uninstalled but present
            // in the old task list. Let us remove them as well.
            if (mTasksSize < oldSize) {
                for (int i = oldSize - 1; i >= mTasksSize; i--) {
                    if (DEBUG) Log.v(TAG,
                            "loading tasks - remove not needed old card - position=" + i);
                    mCards.remove(i);
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean loaded) {
            // If cancelled by system, log it and set task size
            // to the only visible tasks we have till now to keep task
            // removing alive. This should never happen. Just in case.
            if (!loaded) {
                Log.v(TAG, "card constructing was cancelled by system or user");
                mTasksSize = mCounter;
            }

            // Restore original thread priority.
            Process.setThreadPriority(mOrigPri);

            // Set correct view visibilitys
            setVisibility();

            // Notify arrayadapter that data set has changed
            if (DEBUG) Log.v(TAG, "notifiy arrayadapter that data has changed");
            notifyDataSetChanged(false);
            // Notfiy controller that tasks are completly loaded.
            tasksLoaded();
        }

    }

    /**
     * We are holding a list of user expanded states of apps.
     * This class describes one expanded state object.
     */
    private static final class TaskExpandedStates {
        private String mLabel;
        private int mExpandedState;

        public TaskExpandedStates(String label, int expandedState) {
            mLabel = label;
            mExpandedState = expandedState;
        }

        public String getLabel() {
            return mLabel;
        }

        public int getExpandedState() {
            return mExpandedState;
        }

        public void setExpandedState(int expandedState) {
            mExpandedState = expandedState;
        }
    }
}
