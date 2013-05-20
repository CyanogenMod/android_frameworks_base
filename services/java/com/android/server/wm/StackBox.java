/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.wm;

import android.graphics.Rect;
import android.util.Slog;

import static com.android.server.am.ActivityStackSupervisor.HOME_STACK_ID;
import static com.android.server.wm.WindowManagerService.DEBUG_STACK;
import static com.android.server.wm.WindowManagerService.TAG;

import java.io.PrintWriter;

public class StackBox {
    /** Used with {@link WindowManagerService#createStack}. To left of, lower l/r Rect values. */
    public static final int TASK_STACK_GOES_BEFORE = 0;
    /** Used with {@link WindowManagerService#createStack}. To right of, higher l/r Rect values. */
    public static final int TASK_STACK_GOES_AFTER = 1;
    /** Used with {@link WindowManagerService#createStack}. Vertical: lower t/b Rect values. */
    public static final int TASK_STACK_GOES_ABOVE = 2;
    /** Used with {@link WindowManagerService#createStack}. Vertical: higher t/b Rect values. */
    public static final int TASK_STACK_GOES_BELOW = 3;
    /** Used with {@link WindowManagerService#createStack}. Put on a higher layer on display. */
    public static final int TASK_STACK_GOES_OVER = 4;
    /** Used with {@link WindowManagerService#createStack}. Put on a lower layer on display. */
    public static final int TASK_STACK_GOES_UNDER = 5;

    /** The service */
    final WindowManagerService mService;

    /** The display this box sits in. */
    final DisplayContent mDisplayContent;

    /** Non-null indicates this is mFirst or mSecond of a parent StackBox. Null indicates this
     * is this entire size of mDisplayContent. */
    StackBox mParent;

    /** First child, this is null exactly when mStack is non-null. */
    StackBox mFirst;

    /** Second child, this is null exactly when mStack is non-null. */
    StackBox mSecond;

    /** Stack of Tasks, this is null exactly when mFirst and mSecond are non-null. */
    TaskStack mStack;

    /** Content limits relative to the DisplayContent this sits in. */
    Rect mBounds = new Rect();

    /** Relative orientation of mFirst and mSecond. */
    boolean mVertical;

    /** Fraction of mBounds to devote to mFirst, remainder goes to mSecond */
    float mWeight;

    /** Dirty flag. Something inside this or some descendant of this has changed. */
    boolean layoutNeeded;

    /** Used to keep from reallocating a temporary Rect for propagating bounds to child boxes */
    Rect mTmpRect = new Rect();

    StackBox(WindowManagerService service, DisplayContent displayContent, StackBox parent) {
        mService = service;
        mDisplayContent = displayContent;
        mParent = parent;
    }

    /** Propagate #layoutNeeded bottom up. */
    void makeDirty() {
        layoutNeeded = true;
        if (mParent != null) {
            mParent.makeDirty();
        }
    }

    /**
     * Determine if a particular TaskStack is in this StackBox or any of its descendants.
     * @param stackId The TaskStack being considered.
     * @return true if the specified TaskStack is in this box or its descendants. False otherwise.
     */
    boolean contains(int stackId) {
        if (mStack != null) {
            return mStack.mStackId == stackId;
        }
        return mFirst.contains(stackId) || mSecond.contains(stackId);
    }

    /**
     * Return the stackId of the stack that intersects the passed point.
     * @param x coordinate of point.
     * @param y coordinate of point.
     * @return -1 if point is outside of mBounds, otherwise the stackId of the containing stack.
     */
    int stackIdFromPoint(int x, int y) {
        if (!mBounds.contains(x, y)) {
            return -1;
        }
        if (mStack != null) {
            return mStack.mStackId;
        }
        int stackId = mFirst.stackIdFromPoint(x, y);
        if (stackId >= 0) {
            return stackId;
        }
        return mSecond.stackIdFromPoint(x, y);
    }

    /** Determine if this StackBox is the first child or second child.
     * @return true if this is the first child.
     */
    boolean isFirstChild() {
        return mParent != null && mParent.mFirst == this;
    }

    /** Returns the bounds of the specified TaskStack if it is contained in this StackBox.
     * @param stackId the TaskStack to find the bounds of.
     * @return a new Rect with the bounds of stackId if it is within this StackBox, null otherwise.
     */
    Rect getStackBounds(int stackId) {
        if (mStack != null) {
            return mStack.mStackId == stackId ? new Rect(mBounds) : null;
        }
        Rect bounds = mFirst.getStackBounds(stackId);
        if (bounds != null) {
            return bounds;
        }
        return mSecond.getStackBounds(stackId);
    }

    /**
     * Create a new TaskStack relative to a specified one by splitting the StackBox containing
     * the specified TaskStack into two children. The size and position each of the new StackBoxes
     * is determined by the passed parameters.
     * @param stackId The id of the new TaskStack to create.
     * @param relativeStackId The id of the TaskStack to place the new one next to.
     * @param position One of the static TASK_STACK_GOES_xxx positions defined in this class.
     * @param weight The percentage size of the parent StackBox to devote to the new TaskStack.
     * @return The new TaskStack.
     */
    TaskStack split(int stackId, int relativeStackId, int position, float weight) {
        if (mStack == null) {
            // Propagate the split to see if the target task stack is in either sub box.
            TaskStack stack = mFirst.split(stackId, relativeStackId, position, weight);
            if (stack != null) {
                return stack;
            }
            return mSecond.split(stackId, relativeStackId, position, weight);
        }

        // This StackBox contains just a TaskStack.
        if (mStack.mStackId != relativeStackId) {
            // Barking down the wrong stack.
            return null;
        }

        // Found it!
        TaskStack stack = new TaskStack(mService, stackId, mDisplayContent);
        TaskStack firstStack;
        TaskStack secondStack;
        switch (position) {
            default:
            case TASK_STACK_GOES_AFTER:
            case TASK_STACK_GOES_BEFORE:
                mVertical = false;
                if (position == TASK_STACK_GOES_BEFORE) {
                    mWeight = weight;
                    firstStack = stack;
                    secondStack = mStack;
                } else {
                    mWeight = 1.0f - weight;
                    firstStack = mStack;
                    secondStack = stack;
                }
                break;
            case TASK_STACK_GOES_ABOVE:
            case TASK_STACK_GOES_BELOW:
                mVertical = true;
                if (position == TASK_STACK_GOES_ABOVE) {
                    mWeight = weight;
                    firstStack = stack;
                    secondStack = mStack;
                } else {
                    mWeight = 1.0f - weight;
                    firstStack = mStack;
                    secondStack = stack;
                }
                break;
        }

        mFirst = new StackBox(mService, mDisplayContent, this);
        firstStack.mStackBox = mFirst;
        mFirst.mStack = firstStack;

        mSecond = new StackBox(mService, mDisplayContent, this);
        secondStack.mStackBox = mSecond;
        mSecond.mStack = secondStack;

        mStack = null;
        return stack;
    }

    /** Return the stackId of the first mFirst StackBox with a non-null mStack */
    int getStackId() {
        if (mStack != null) {
            return mStack.mStackId;
        }
        return mFirst.getStackId();
    }

    /** Remove this box and propagate its sibling's content up to their parent.
     * @return The first stackId of the resulting StackBox. */
    int remove() {
        if (mStack != null) {
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: removing stackId=" + mStack.mStackId);
            mDisplayContent.mStackHistory.remove(mStack);
        }
        mDisplayContent.layoutNeeded = true;

        if (mParent == null) {
            // This is the top-plane stack.
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: removing top plane.");
            mDisplayContent.removeStackBox(this);
            return HOME_STACK_ID;
        }

        StackBox sibling = isFirstChild() ? mParent.mSecond : mParent.mFirst;
        StackBox grandparent = mParent.mParent;
        sibling.mParent = grandparent;
        if (grandparent == null) {
            // mParent is a top-plane stack. Now sibling will be.
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: grandparent null");
            mDisplayContent.removeStackBox(mParent);
            mDisplayContent.addStackBox(sibling, true);
        } else {
            if (DEBUG_STACK) Slog.i(TAG, "StackBox.remove: grandparent getting sibling");
            if (mParent.isFirstChild()) {
                grandparent.mFirst = sibling;
            } else {
                grandparent.mSecond = sibling;
            }
        }
        return sibling.getStackId();
    }

    boolean resize(int stackId, float weight) {
        if (mStack == null) {
            return mFirst.resize(stackId, weight) || mSecond.resize(stackId, weight);
        }
        if (mStack.mStackId == stackId) {
            mParent.mWeight = isFirstChild() ? weight : 1.0f - weight;
            return true;
        }
        return false;
    }

    /** If this is a terminal StackBox (contains a TaskStack) set the bounds.
     * @param bounds The rectangle to set the bounds to.
     * @return True if the bounds changed, false otherwise. */
    boolean setStackBoxSizes(Rect bounds) {
        boolean change;
        if (mStack != null) {
            change = !mBounds.equals(bounds);
            if (change) {
                mBounds.set(bounds);
                mStack.setBounds(bounds);
            }
        } else {
            mTmpRect.set(bounds);
            if (mVertical) {
                final int height = bounds.height();
                int firstHeight = (int)(height * mWeight);
                mTmpRect.bottom = bounds.top + firstHeight;
                change = mFirst.setStackBoxSizes(mTmpRect);
                mTmpRect.top = mTmpRect.bottom;
                mTmpRect.bottom = bounds.top + height;
                change |= mSecond.setStackBoxSizes(mTmpRect);
            } else {
                final int width = bounds.width();
                int firstWidth = (int)(width * mWeight);
                mTmpRect.right = bounds.left + firstWidth;
                change = mFirst.setStackBoxSizes(mTmpRect);
                mTmpRect.left = mTmpRect.right;
                mTmpRect.right = bounds.left + width;
                change |= mSecond.setStackBoxSizes(mTmpRect);
            }
        }
        return change;
    }

    void resetAnimationBackgroundAnimator() {
        if (mStack != null) {
            mStack.resetAnimationBackgroundAnimator();
            return;
        }
        mFirst.resetAnimationBackgroundAnimator();
        mSecond.resetAnimationBackgroundAnimator();
    }

    boolean animateDimLayers() {
        if (mStack != null) {
            return mStack.animateDimLayers();
        }
        boolean result = mFirst.animateDimLayers();
        result |= mSecond.animateDimLayers();
        return result;
    }

    void resetDimming() {
        if (mStack != null) {
            mStack.resetDimmingTag();
            return;
        }
        mFirst.resetDimming();
        mSecond.resetDimming();
    }

    boolean isDimming() {
        if (mStack != null) {
            return mStack.isDimming();
        }
        boolean result = mFirst.isDimming();
        result |= mSecond.isDimming();
        return result;
    }

    void stopDimmingIfNeeded() {
        if (mStack != null) {
            mStack.stopDimmingIfNeeded();
            return;
        }
        mFirst.stopDimmingIfNeeded();
        mSecond.stopDimmingIfNeeded();
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("mParent="); pw.println(mParent);
        pw.print(prefix); pw.print("mBounds="); pw.print(mBounds.toShortString());
            pw.print(" mVertical="); pw.print(mVertical);
            pw.print(" layoutNeeded="); pw.println(layoutNeeded);
        if (mFirst != null) {
            pw.print(prefix); pw.print("mFirst="); pw.println(System.identityHashCode(mFirst));
            mFirst.dump(prefix + "  ", pw);
            pw.print(prefix); pw.print("mSecond="); pw.println(System.identityHashCode(mSecond));
            mSecond.dump(prefix + "  ", pw);
        } else {
            pw.print(prefix); pw.print("mStack="); pw.println(mStack);
            mStack.dump(prefix + "  ", pw);
        }
    }

    @Override
    public String toString() {
        if (mStack != null) {
            return "Box{" + hashCode() + " stack=" + mStack.mStackId + "}";
        }
        return "Box{" + hashCode() + " parent=" + System.identityHashCode(mParent)
                + " first=" + System.identityHashCode(mFirst)
                + " second=" + System.identityHashCode(mSecond) + "}";
    }
}
