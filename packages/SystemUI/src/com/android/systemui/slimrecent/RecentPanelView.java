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
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;

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

    public static final String TASK_PACKAGE_IDENTIFIER = "#ident:";

    private static final int EXPANDED_STATE_UNKNOWN  = 0;
    public static final int EXPANDED_STATE_EXPANDED  = 1;
    public static final int EXPANDED_STATE_COLLAPSED = 2;
    public static final int EXPANDED_STATE_BY_SYSTEM = 4;

    public static final int EXPANDED_MODE_AUTO    = 0;
    private static final int EXPANDED_MODE_ALWAYS = 1;
    private static final int EXPANDED_MODE_NEVER  = 2;

    private static final int MENU_APP_DETAILS_ID   = 0;
    private static final int MENU_APP_WIPE_ID      = 1;
    private static final int MENU_APP_STOP_ID      = 2;
    private static final int MENU_APP_PLAYSTORE_ID = 3;
    private static final int MENU_APP_AMAZON_ID    = 4;

    private static final String PLAYSTORE_REFERENCE = "com.android.vending";
    private static final String AMAZON_REFERENCE    = "com.amazon.venezia";

    private static final String PLAYSTORE_APP_URI_QUERY = "market://details?id=";
    private static final String AMAZON_APP_URI_QUERY    = "amzn://apps/android?p=";

    private final Context mContext;
    private final CardListView mListView;
    private final ImageView mEmptyRecentView;

    private final RecentController mController;

    // Our array adapter holding all cards
    private CardArrayAdapter mCardArrayAdapter;
    // Array list of all current cards
    private ArrayList<Card> mCards;
    // Array list of all current tasks
    private final ArrayList<TaskDescription> mTasks = new ArrayList<TaskDescription>();
    // Our first task which is not displayed but needed for internal references.
    private TaskDescription mFirstTask;
    // Array list of all expanded states of apps accessed during the session
    private final ArrayList<TaskExpandedStates> mExpandedTaskStates =
            new ArrayList<TaskExpandedStates>();

    private boolean mCancelledByUser;
    private boolean mTasksLoaded;
    private boolean mIsLoading;
    private int mTasksSize;

    private int mMainGravity;
    private float mScaleFactor;
    private int mExpandedMode = EXPANDED_MODE_AUTO;

    private PopupMenu mPopup;

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

        // Listen for onLongClick to open popup menu
        card.setOnLongClickListener(new Card.OnLongCardClickListener() {
            @Override
            public boolean onLongClick(Card card, View view) {
                constructMenu(
                        (ImageButton) view.findViewById(R.id.card_header_button_expand),
                        td);
                return true;
            }
        });

        // App icon has own onLongClick action. Listen for it and
        // process the favorite action for it.
        card.addPartialOnLongClickListener(Card.CLICK_LISTENER_THUMBNAIL_VIEW,
                new Card.OnLongCardClickListener() {
            @Override
            public boolean onLongClick(Card card, View view) {
                RecentImageView favoriteIcon =
                        (RecentImageView) view.findViewById(R.id.card_thumbnail_favorite);
                favoriteIcon.setVisibility(td.getIsFavorite() ? View.INVISIBLE : View.VISIBLE);
                handleFavoriteEntry(td);
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
     * Handle favorite task entry (add or remove) if user longpressed on app icon.
     */
    private void handleFavoriteEntry(TaskDescription td) {
        ContentResolver resolver = mContext.getContentResolver();
        final String favorites = Settings.System.getStringForUser(
                    resolver, Settings.System.RECENT_PANEL_FAVORITES,
                    UserHandle.USER_CURRENT);
        String entryToSave = "";

        if (!td.getIsFavorite()) {
            if (favorites != null && !favorites.isEmpty()) {
                entryToSave += favorites + "|";
            }
            entryToSave += td.identifier;
        } else {
            if (favorites == null) {
                return;
            }
            for (String favorite : favorites.split("\\|")) {
                if (favorite.equals(td.identifier)) {
                    continue;
                }
                entryToSave += favorite + "|";
            }
            if (!entryToSave.isEmpty()) {
                entryToSave = entryToSave.substring(0, entryToSave.length() - 1);
            }
        }

        td.setIsFavorite(!td.getIsFavorite());

        Settings.System.putStringForUser(
                resolver, Settings.System.RECENT_PANEL_FAVORITES,
                entryToSave,
                UserHandle.USER_CURRENT);
    }

    /**
     * Construct popup menu for longpress.
     */
    private void constructMenu(final View selectedView, final TaskDescription td) {
        if (selectedView == null) {
            return;
        }
        // Force theme change to choose custom defined menu layout.
        final Context layoutContext = new ContextThemeWrapper(mContext, R.style.RecentBaseStyle);

        final PopupMenu popup = new PopupMenu(layoutContext, selectedView, Gravity.RIGHT);
        mPopup = popup;

        // If recent panel is drawn on the right edge we allow the menu
        // if needed to draw over the left container edge.
        popup.setAllowLeftOverdraw(mMainGravity == Gravity.RIGHT);

        // Add app detail menu entry.
        popup.getMenu().add(0, MENU_APP_DETAILS_ID, 0,
                mContext.getResources().getString(R.string.status_bar_recent_inspect_item_title));


        if (Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DEVELOPMENT_SHORTCUT, 0) == 1) {
            popup.getMenu().add(0, MENU_APP_STOP_ID, 0,
                    mContext.getResources().getString(R.string.advanced_dev_option_force_stop));
            try {
                PackageManager pm = (PackageManager) mContext.getPackageManager();
                ApplicationInfo mAppInfo = pm.getApplicationInfo(td.packageName, 0);
                DevicePolicyManager mDpm = (DevicePolicyManager) mContext.
                        getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (!((mAppInfo.flags&(ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA))
                        == ApplicationInfo.FLAG_SYSTEM
                        || mDpm.packageHasActiveAdmins(td.packageName))) {
                    popup.getMenu().add(0, MENU_APP_WIPE_ID, 0,
                            mContext.getResources().getString(R.string.advanced_dev_option_wipe_app));
                    Log.d(TAG, "Not a 'special' application");
                }
            } catch (NameNotFoundException ex) {
                Log.e(TAG, "Failed looking up ApplicationInfo for " + td.packageName, ex);
            }
        }

        // Add playstore or amazon entry if it is provided by the application.
        if (checkAppInstaller(td.packageName, PLAYSTORE_REFERENCE)) {
            popup.getMenu().add(0, MENU_APP_PLAYSTORE_ID, 0,
                    getApplicationLabel(PLAYSTORE_REFERENCE));
        } else if (checkAppInstaller(td.packageName, AMAZON_REFERENCE)) {
            popup.getMenu().add(0, MENU_APP_AMAZON_ID, 0,
                    getApplicationLabel(AMAZON_REFERENCE));
        }

        // Actually peform the actions onClick.
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == MENU_APP_DETAILS_ID) {
                    startApplicationDetailsActivity(td.packageName, null, null);
                } else if (item.getItemId() == MENU_APP_STOP_ID) {
                    ActivityManager am = (ActivityManager)mContext.getSystemService(
                            Context.ACTIVITY_SERVICE);
                    am.forceStopPackage(td.packageName);
                    removeApplication(td);
                } else if (item.getItemId() == MENU_APP_WIPE_ID) {
                    ActivityManager am = (ActivityManager) mContext.
                            getSystemService(Context.ACTIVITY_SERVICE);
                    am.clearApplicationUserData(td.packageName,
                            new FakeClearUserDataObserver());
                    removeApplication(td);
                } else if (item.getItemId() == MENU_APP_PLAYSTORE_ID) {
                    startApplicationDetailsActivity(null,
                            PLAYSTORE_APP_URI_QUERY + td.packageName, PLAYSTORE_REFERENCE);
                } else if (item.getItemId() == MENU_APP_AMAZON_ID) {
                    startApplicationDetailsActivity(null,
                            AMAZON_APP_URI_QUERY + td.packageName, AMAZON_REFERENCE);
                }
                return true;
            }
        });
        popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
            public void onDismiss(PopupMenu menu) {
                mPopup = null;
            }
        });
        popup.show();
    }

    /**
     * Check if the requested app was installed by the reference store.
     */
    private boolean checkAppInstaller(String packageName, String reference) {
        if (packageName == null) {
            return false;
        }
        PackageManager pm = mContext.getPackageManager();
        if (!isReferenceInstalled(reference, pm)) {
            return false;
        }

        String installer = pm.getInstallerPackageName(packageName);
        if (DEBUG) Log.d(TAG, "Package was installed by: " + installer);
        if (reference.equals(installer)) {
            return true;
        }
        return false;
    }

    /**
     * Check is store reference is installed.
     */
    private boolean isReferenceInstalled(String packagename, PackageManager pm) {
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (NameNotFoundException e) {
            if (DEBUG) Log.e(TAG, "Store is not installed: " + packagename, e);
            return false;
        }
    }

    /**
     * Get application launcher label of installed references.
     */
    private String getApplicationLabel(String packageName) {
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent = pm.getLaunchIntentForPackage(packageName);
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        if (resolveInfo != null) {
            return resolveInfo.activityInfo.loadLabel(pm).toString();
        }
        return null;
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
    protected boolean removeAllApplications() {
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        boolean hasFavorite = false;
        final int oldTaskSize = mTasks.size() - 1;
        for (int i = oldTaskSize; i >= 0; i--) {
            TaskDescription td = mTasks.get(i);
            // User favorites are not removed.
            if (td.getIsFavorite()) {
                hasFavorite = true;
                continue;
            }
            // Remove from task stack.
            if (am != null) {
                am.removeTask(td.persistentTaskId, ActivityManager.REMOVE_TASK_KILL_PROCESS);
            }
            // Remove from task list.
            mTasks.remove(td);
            // Remove the card.
            removeRecentCard(td);
            // Notify ArrayAdapter about the change.
            mCardArrayAdapter.notifyDataSetChanged();
            // Remove bitmap and expanded state.
            removeApplicationBitmapCacheAndExpandedState(td);
            // Correct global task size.
            mTasksSize--;
        }
        return !hasFavorite;
    }

    private void removeRecentCard(TaskDescription td) {
        for (int i = 0; i < mCards.size(); i++) {
            RecentCard card = (RecentCard) mCards.get(i);
            if (card != null && card.getPersistentTaskId() == td.persistentTaskId) {
                mCards.remove(i);
                return;
            }
        }
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
                .removeBitmapFromMemCache(td.identifier);
        // Remove from expanded state list.
        removeExpandedTaskState(td.identifier);
    }

    /**
     * Start application or move to forground if still active.
     */
    private void startApplication(TaskDescription td) {
        // Starting app is requested by the user.
        // Move it to foreground or start it with custom animation.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (td.taskId >= 0) {
            // This is an active task; it should just go to the foreground.
            am.moveTaskToFront(td.taskId, ActivityManager.MOVE_TASK_WITH_HOME, getAnimation());
        } else {
            final Intent intent = td.intent;
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                    | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (DEBUG) Log.v(TAG, "Starting activity " + intent);
            try {
                mContext.startActivityAsUser(intent, getAnimation(),
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
     * Start application details screen or play/amazon store details.
     */
    private void startApplicationDetailsActivity(
            String packageName, String uri, String uriReference) {
        // Starting app details screen is requested by the user.
        // Start it with custom animation.
        Intent intent = null;
        if (packageName != null) {
            // App detail screen is requested. Prepare the intent.
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null));
        } else if (uri != null && uriReference != null) {
            // Store app detail is requested. Prepare the intent.
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(uri));
            // Exclude from recents if the store is not in our task list.
            if (!storeIsInTaskList(uriReference)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            }
        }
        if (intent == null) {
            return;
        }
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext)
                .addNextIntentWithParentStack(intent).startActivities(getAnimation());
        exit();
    }

    /**
     * Get custom animation for app starting.
     * @return Bundle
     */
    private Bundle getAnimation() {
        return ActivityOptions.makeCustomAnimation(mContext,
                mMainGravity == Gravity.RIGHT ? com.android.internal.R.anim.recent_screen_enter
                        : com.android.internal.R.anim.recent_screen_enter_left,
                com.android.internal.R.anim.recent_screen_fade_out).toBundle();
    }

    /**
     * Check if the requested store is in the task list to prevent it gets excluded.
     */
    private boolean storeIsInTaskList(String uriReference) {
        if (mFirstTask != null && uriReference.equals(mFirstTask.packageName)) {
            return true;
        }
        for (TaskDescription task : mTasks) {
            if (uriReference.equals(task.packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a TaskDescription, returning null if the title or icon is null.
     */
    private TaskDescription createTaskDescription(int taskId, int persistentTaskId,
            Intent baseIntent, ComponentName origActivity,
            CharSequence description, boolean isFavorite, int expandedState) {

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

            String identifier = TASK_PACKAGE_IDENTIFIER;
            final ComponentName component = intent.getComponent();
            if (component != null) {
                identifier += component.flattenToString();
            } else {
                identifier += info.packageName;
            }

            if (title != null && title.length() > 0) {
                if (DEBUG) Log.v(TAG, "creating activity desc for id="
                        + persistentTaskId + ", label=" + title);

                final TaskDescription item = new TaskDescription(taskId,
                        persistentTaskId, resolveInfo, baseIntent, info.packageName,
                        identifier, description, isFavorite, expandedState);
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

        // Check and get user favorites.
        final String favorites = Settings.System.getStringForUser(
                mContext.getContentResolver(), Settings.System.RECENT_PANEL_FAVORITES,
                UserHandle.USER_CURRENT);
        final ArrayList<String> favList = new ArrayList<String>();
        final ArrayList<TaskDescription> nonFavoriteTasks = new ArrayList<TaskDescription>();
        if (favorites != null && !favorites.isEmpty()) {
            for (String favorite : favorites.split("\\|")) {
                favList.add(favorite);
            }
        }

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
        boolean loadOneExcluded = true;
        // Get current task list. We do not need to do it in background. We only load MAX_TASKS.
        for (int i = 0, index = 0; i < numTasks && (index < MAX_TASKS); ++i) {
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
                loadOneExcluded = false;
                continue;
            }

            // Don't load excluded activities.
            if (!loadOneExcluded && (recentInfo.baseIntent.getFlags()
                    & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0) {
                continue;
            }

            loadOneExcluded = false;

            TaskDescription item = createTaskDescription(recentInfo.id,
                    recentInfo.persistentId, recentInfo.baseIntent,
                    recentInfo.origActivity, recentInfo.description,
                    false, EXPANDED_STATE_UNKNOWN);

            if (item != null) {
                for (String fav : favList) {
                    if (fav.equals(item.identifier)) {
                        item.setIsFavorite(true);
                        break;
                    }
                }

                if (i == 0) {
                    // Skip the first task for our list but save it for later use.
                    mFirstTask = item;
                } else {
                    // FirstExpandedItems value forces to show always the app screenshot
                    // if the old state is not known and the user has set expanded mode to auto.
                    // On all other items we check if they were expanded from the user
                    // in last known recent app list and restore the state. This counts as well
                    // if expanded mode is always or never.
                    int oldState = getExpandedState(item);
                    if ((oldState & EXPANDED_STATE_BY_SYSTEM) != 0) {
                        oldState &= ~EXPANDED_STATE_BY_SYSTEM;
                    }
                    if (DEBUG) Log.v(TAG, "old expanded state = " + oldState);
                    if (firstItems < firstExpandedItems) {
                        if (mExpandedMode != EXPANDED_MODE_NEVER) {
                            oldState |= EXPANDED_STATE_BY_SYSTEM;
                        }
                        item.setExpandedState(oldState);
                        // The first tasks are always added to the task list.
                        mTasks.add(item);
                    } else {
                        if (mExpandedMode == EXPANDED_MODE_ALWAYS) {
                            oldState |= EXPANDED_STATE_BY_SYSTEM;
                        }
                        item.setExpandedState(oldState);
                        // Favorite tasks are added next. Non favorite
                        // we hold for a short time in an extra list.
                        if (item.getIsFavorite()) {
                            mTasks.add(item);
                        } else {
                            nonFavoriteTasks.add(item);
                        }
                    }
                    firstItems++;
                }
            }
        }

        // Add now the non favorite tasks to the final task list.
        for (TaskDescription item : nonFavoriteTasks) {
            mTasks.add(item);
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
                if (item.identifier.equals(expandedState.getIdentifier())) {
                    updated = true;
                    expandedState.setExpandedState(item.getExpandedState());
                }
            }
            if (!updated) {
                mExpandedTaskStates.add(
                        new TaskExpandedStates(
                                item.identifier, item.getExpandedState()));
            }
        }
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Get expanded state of the app.
     */
    private int getExpandedState(TaskDescription item) {
        for (TaskExpandedStates oldTask : mExpandedTaskStates) {
            if (DEBUG) Log.v(TAG, "old task launch uri = "+ oldTask.getIdentifier()
                    + " new task launch uri = " + item.identifier);
            if (item.identifier.equals(oldTask.getIdentifier())) {
                    return oldTask.getExpandedState();
            }
        }
        return EXPANDED_STATE_UNKNOWN;
    }

    /**
     * We are holding a list of user expanded state of apps.
     * Remove expanded state entry due that app was removed by the user.
     */
    private void removeExpandedTaskState(String identifier) {
        TaskExpandedStates expandedStateToDelete = null;
        for (TaskExpandedStates expandedState : mExpandedTaskStates) {
            if (expandedState.getIdentifier().equals(identifier)) {
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

    protected void dismissPopup() {
        if (mPopup != null) {
            mPopup.dismiss();
            mPopup = null;
        }
    }

    protected void setMainGravity(int gravity) {
        mMainGravity = gravity;
    }

    protected void setScaleFactor(float factor) {
        mScaleFactor = factor;
    }

    protected void setExpandedMode(int mode) {
        mExpandedMode = mode;
    }

    protected boolean hasFavorite() {
        for (TaskDescription td : mTasks) {
            if (td.getIsFavorite()) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasClearableTasks() {
        for (TaskDescription td : mTasks) {
            if (!td.getIsFavorite()) {
                return true;
            }
        }
        return false;
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

            final int oldSize = mCards.size();
            final int newSize = mTasks.size();
            mCounter = 0;

            // Construct or update cards and publish cards recursive with current tasks.
            for (int i = newSize - 1; i >= 0; i--) {
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
                        card.updateCardContent(task, mScaleFactor);
                        card = assignListeners(card, task);
                    }
                }

                // No old card was present to update....so add a new one.
                if (card == null) {
                    if (DEBUG) Log.v(TAG, "loading tasks - create new card");
                    card = new RecentCard(mContext, task, mScaleFactor);
                    card = assignListeners(card, task);
                    mCards.add(card);
                }

                mCounter++;
            }

            // We may have unused cards left. Eg app was uninstalled but present
            // in the old task list. Let us remove them as well.
            if (newSize < oldSize) {
                for (int i = oldSize - 1; i >= newSize; i--) {
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
        private String mIdentifier;
        private int mExpandedState;

        public TaskExpandedStates(String identifier, int expandedState) {
            mIdentifier = identifier;
            mExpandedState = expandedState;
        }

        public String getIdentifier() {
            return mIdentifier;
        }

        public int getExpandedState() {
            return mExpandedState;
        }

        public void setExpandedState(int expandedState) {
            mExpandedState = expandedState;
        }
    }

    class FakeClearUserDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
        }
    }
}
