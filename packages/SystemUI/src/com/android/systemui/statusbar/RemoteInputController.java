/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import com.android.internal.util.Preconditions;
import com.android.systemui.statusbar.phone.StatusBarWindowManager;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.RemoteInputView;

import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Keeps track of the currently active {@link RemoteInputView}s.
 */
public class RemoteInputController {

    private final ArrayList<Pair<WeakReference<NotificationData.Entry>, Object>> mOpen
            = new ArrayList<>();
    private final ArrayMap<String, Object> mSpinning = new ArrayMap<>();
    private final ArrayList<Callback> mCallbacks = new ArrayList<>(3);
    private final HeadsUpManager mHeadsUpManager;

    public RemoteInputController(StatusBarWindowManager sbwm, HeadsUpManager headsUpManager) {
        addCallback(sbwm);
        mHeadsUpManager = headsUpManager;
    }

    /**
     * Adds a currently active remote input.
     *
     * @param entry the entry for which a remote input is now active.
     * @param token a token identifying the view that is managing the remote input
     */
    public void addRemoteInput(NotificationData.Entry entry, Object token) {
        Preconditions.checkNotNull(entry);
        Preconditions.checkNotNull(token);

        boolean found = pruneWeakThenRemoveAndContains(
                entry /* contains */, null /* remove */, token /* removeToken */);
        if (!found) {
            mOpen.add(new Pair<>(new WeakReference<>(entry), token));
        }

        apply(entry);
    }

    /**
     * Removes a currently active remote input.
     *
     * @param entry the entry for which a remote input should be removed.
     * @param token a token identifying the view that is requesting the removal. If non-null,
     *              the entry is only removed if the token matches the last added token for this
     *              entry. If null, the entry is removed regardless.
     */
    public void removeRemoteInput(NotificationData.Entry entry, Object token) {
        Preconditions.checkNotNull(entry);

        pruneWeakThenRemoveAndContains(null /* contains */, entry /* remove */, token);

        apply(entry);
    }

    /**
     * Adds a currently spinning (i.e. sending) remote input.
     *
     * @param key the key of the entry that's spinning.
     * @param token the token of the view managing the remote input.
     */
    public void addSpinning(String key, Object token) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(token);

        mSpinning.put(key, token);
    }

    /**
     * Removes a currently spinning remote input.
     *
     * @param key the key of the entry for which a remote input should be removed.
     * @param token a token identifying the view that is requesting the removal. If non-null,
     *              the entry is only removed if the token matches the last added token for this
     *              entry. If null, the entry is removed regardless.
     */
    public void removeSpinning(String key, Object token) {
        Preconditions.checkNotNull(key);

        if (token == null || mSpinning.get(key) == token) {
            mSpinning.remove(key);
        }
    }

    public boolean isSpinning(String key) {
        return mSpinning.containsKey(key);
    }

    private void apply(NotificationData.Entry entry) {
        mHeadsUpManager.setRemoteInputActive(entry, isRemoteInputActive(entry));
        boolean remoteInputActive = isRemoteInputActive();
        int N = mCallbacks.size();
        for (int i = 0; i < N; i++) {
            mCallbacks.get(i).onRemoteInputActive(remoteInputActive);
        }
    }

    /**
     * @return true if {@param entry} has an active RemoteInput
     */
    public boolean isRemoteInputActive(NotificationData.Entry entry) {
        return pruneWeakThenRemoveAndContains(entry /* contains */, null /* remove */,
                null /* removeToken */);
    }

    /**
     * @return true if any entry has an active RemoteInput
     */
    public boolean isRemoteInputActive() {
        pruneWeakThenRemoveAndContains(null /* contains */, null /* remove */,
                null /* removeToken */);
        return !mOpen.isEmpty();
    }

    /**
     * Prunes dangling weak references, removes entries referring to {@param remove} and returns
     * whether {@param contains} is part of the array in a single loop.
     * @param remove if non-null, removes this entry from the active remote inputs
     * @param removeToken if non-null, only removes an entry if this matches the token when the
     *                    entry was added.
     * @return true if {@param contains} is in the set of active remote inputs
     */
    private boolean pruneWeakThenRemoveAndContains(
            NotificationData.Entry contains, NotificationData.Entry remove, Object removeToken) {
        boolean found = false;
        for (int i = mOpen.size() - 1; i >= 0; i--) {
            NotificationData.Entry item = mOpen.get(i).first.get();
            Object itemToken = mOpen.get(i).second;
            boolean removeTokenMatches = (removeToken == null || itemToken == removeToken);

            if (item == null || (item == remove && removeTokenMatches)) {
                mOpen.remove(i);
            } else if (item == contains) {
                if (removeToken != null && removeToken != itemToken) {
                    // We need to update the token. Remove here and let caller reinsert it.
                    mOpen.remove(i);
                } else {
                    found = true;
                }
            }
        }
        return found;
    }


    public void addCallback(Callback callback) {
        Preconditions.checkNotNull(callback);
        mCallbacks.add(callback);
    }

    public void remoteInputSent(NotificationData.Entry entry) {
        int N = mCallbacks.size();
        for (int i = 0; i < N; i++) {
            mCallbacks.get(i).onRemoteInputSent(entry);
        }
    }

    public void closeRemoteInputs() {
        if (mOpen.size() == 0) {
            return;
        }

        // Make a copy because closing the remote inputs will modify mOpen.
        ArrayList<NotificationData.Entry> list = new ArrayList<>(mOpen.size());
        for (int i = mOpen.size() - 1; i >= 0; i--) {
            NotificationData.Entry item = mOpen.get(i).first.get();
            if (item != null && item.row != null) {
                list.add(item);
            }
        }

        for (int i = list.size() - 1; i >= 0; i--) {
            NotificationData.Entry item = list.get(i);
            if (item.row != null) {
                item.row.closeRemoteInput();
            }
        }
    }

    public interface Callback {
        default void onRemoteInputActive(boolean active) {}

        default void onRemoteInputSent(NotificationData.Entry entry) {}
    }
}
