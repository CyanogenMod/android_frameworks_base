/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.webkit;

import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebViewCore.EventHub;

import com.android.internal.os.SomeArgs;

import java.util.ArrayList;

/**
 * This class injects accessibility into WebViews with disabled JavaScript or
 * WebViews with enabled JavaScript but for which we have no accessibility
 * script to inject.
 * </p>
 * Note: To avoid changes in the framework upon changing the available
 *       navigation axis, or reordering the navigation axis, or changing
 *       the key bindings, or defining sequence of actions to be bound to
 *       a given key this class is navigation axis agnostic. It is only
 *       aware of one navigation axis which is in fact the default behavior
 *       of webViews while using the DPAD/TrackBall.
 * </p>
 * In general a key binding is a mapping from modifiers + key code to
 * a sequence of actions. For more detail how to specify key bindings refer to
 * {@link android.provider.Settings.Secure#ACCESSIBILITY_WEB_CONTENT_KEY_BINDINGS}.
 * </p>
 * The possible actions are invocations to
 * {@link #setCurrentAxis(int, boolean, String)}, or
 * {@link #traverseGivenAxis(int, int, boolean, String, boolean)}
 * {@link #performAxisTransition(int, int, boolean, String)}
 * referred via the values of:
 * {@link #ACTION_SET_CURRENT_AXIS},
 * {@link #ACTION_TRAVERSE_CURRENT_AXIS},
 * {@link #ACTION_TRAVERSE_GIVEN_AXIS},
 * {@link #ACTION_PERFORM_AXIS_TRANSITION},
 * respectively.
 * The arguments for the action invocation are specified as offset
 * hexademical pairs. Note the last argument of the invocation
 * should NOT be specified in the binding as it is provided by
 * this class. For details about the key binding implementation
 * refer to {@link AccessibilityWebContentKeyBinding}.
 */
class AccessibilityInjectorFallback {
    private static final String LOG_TAG = "AccessibilityInjector";

    private static final boolean DEBUG = true;

    private static final int ACTION_SET_CURRENT_AXIS = 0;
    private static final int ACTION_TRAVERSE_CURRENT_AXIS = 1;
    private static final int ACTION_TRAVERSE_GIVEN_AXIS = 2;
    private static final int ACTION_PERFORM_AXIS_TRANSITION = 3;
    private static final int ACTION_TRAVERSE_DEFAULT_WEB_VIEW_BEHAVIOR_AXIS = 4;

    /** Timeout after which asynchronous granular movement is aborted. */
    private static final int MODIFY_SELECTION_TIMEOUT = 500;

    // WebView navigation axes from WebViewCore.h, plus an additional axis for
    // the default behavior.
    private static final int NAVIGATION_AXIS_CHARACTER = 0;
    private static final int NAVIGATION_AXIS_WORD = 1;
    private static final int NAVIGATION_AXIS_SENTENCE = 2;
    @SuppressWarnings("unused")
    private static final int NAVIGATION_AXIS_HEADING = 3;
    @SuppressWarnings("unused")
    private static final int NAVIGATION_AXIS_SIBLING = 4;
    @SuppressWarnings("unused")
    private static final int NAVIGATION_AXIS_PARENT_FIRST_CHILD = 5;
    private static final int NAVIGATION_AXIS_DOCUMENT = 6;
    private static final int NAVIGATION_AXIS_DEFAULT_WEB_VIEW_BEHAVIOR = 7;

    // WebView navigation directions from WebViewCore.h.
    private static final int NAVIGATION_DIRECTION_BACKWARD = 0;
    private static final int NAVIGATION_DIRECTION_FORWARD = 1;

    // these are the same for all instances so make them process wide
    private static ArrayList<AccessibilityWebContentKeyBinding> sBindings =
        new ArrayList<AccessibilityWebContentKeyBinding>();

    // handle to the WebViewClassic this injector is associated with.
    private final WebViewClassic mWebView;
    private final WebView mWebViewInternal;

    // Event scheduled for sending as soon as we receive the selected text.
    private AccessibilityEvent mScheduledEvent;

    // Token required to send the scheduled event.
    private int mScheduledToken = 0;

    // the current traversal axis
    private int mCurrentAxis = 2; // sentence

    // we need to consume the up if we have handled the last down
    private boolean mLastDownEventHandled;

    // getting two empty selection strings in a row we let the WebView handle the event
    private boolean mIsLastSelectionStringNull;

    // keep track of last direction
    private int mLastDirection;

    // Lock used for asynchronous selection callback.
    private final Object mCallbackLock = new Object();

    // Whether the asynchronous selection callback was received.
    private boolean mCallbackReceived;

    // Whether the asynchronous selection callback succeeded.
    private boolean mCallbackResult;

    /**
     * Creates a new injector associated with a given {@link WebViewClassic}.
     *
     * @param webView The associated WebViewClassic.
     */
    public AccessibilityInjectorFallback(WebViewClassic webView) {
        mWebView = webView;
        mWebViewInternal = mWebView.getWebView();
        ensureWebContentKeyBindings();
    }

    /**
     * Processes a key down <code>event</code>.
     *
     * @return True if the event was processed.
     */
    public boolean onKeyEvent(KeyEvent event) {
        // We do not handle ENTER in any circumstances.
        if (isEnterActionKey(event.getKeyCode())) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            return mLastDownEventHandled;
        }

        mLastDownEventHandled = false;

        AccessibilityWebContentKeyBinding binding = null;
        for (AccessibilityWebContentKeyBinding candidate : sBindings) {
            if (event.getKeyCode() == candidate.getKeyCode()
                    && event.hasModifiers(candidate.getModifiers())) {
                binding = candidate;
                break;
            }
        }

        if (binding == null) {
            return false;
        }

        for (int i = 0, count = binding.getActionCount(); i < count; i++) {
            int actionCode = binding.getActionCode(i);
            String contentDescription = Integer.toHexString(binding.getAction(i));
            switch (actionCode) {
                case ACTION_SET_CURRENT_AXIS:
                    int axis = binding.getFirstArgument(i);
                    boolean sendEvent = (binding.getSecondArgument(i) == 1);
                    setCurrentAxis(axis, sendEvent, contentDescription);
                    mLastDownEventHandled = true;
                    break;
                case ACTION_TRAVERSE_CURRENT_AXIS:
                    int direction = binding.getFirstArgument(i);
                    // on second null selection string in same direction - WebView handles the event
                    if (direction == mLastDirection && mIsLastSelectionStringNull) {
                        mIsLastSelectionStringNull = false;
                        return false;
                    }
                    mLastDirection = direction;
                    sendEvent = (binding.getSecondArgument(i) == 1);
                    mLastDownEventHandled = traverseGivenAxis(
                            direction, mCurrentAxis, sendEvent, contentDescription, false);
                    break;
                case ACTION_TRAVERSE_GIVEN_AXIS:
                    direction = binding.getFirstArgument(i);
                    // on second null selection string in same direction => WebView handle the event
                    if (direction == mLastDirection && mIsLastSelectionStringNull) {
                        mIsLastSelectionStringNull = false;
                        return false;
                    }
                    mLastDirection = direction;
                    axis =  binding.getSecondArgument(i);
                    sendEvent = (binding.getThirdArgument(i) == 1);
                    traverseGivenAxis(direction, axis, sendEvent, contentDescription, false);
                    mLastDownEventHandled = true;
                    break;
                case ACTION_PERFORM_AXIS_TRANSITION:
                    int fromAxis = binding.getFirstArgument(i);
                    int toAxis = binding.getSecondArgument(i);
                    sendEvent = (binding.getThirdArgument(i) == 1);
                    performAxisTransition(fromAxis, toAxis, sendEvent, contentDescription);
                    mLastDownEventHandled = true;
                    break;
                case ACTION_TRAVERSE_DEFAULT_WEB_VIEW_BEHAVIOR_AXIS:
                    // This is a special case since we treat the default WebView navigation
                    // behavior as one of the possible navigation axis the user can use.
                    // If we are not on the default WebView navigation axis this is NOP.
                    if (mCurrentAxis == NAVIGATION_AXIS_DEFAULT_WEB_VIEW_BEHAVIOR) {
                        // While WebVew handles navigation we do not get null selection
                        // strings so do not check for that here as the cases above.
                        mLastDirection = binding.getFirstArgument(i);
                        sendEvent = (binding.getSecondArgument(i) == 1);
                        traverseGivenAxis(mLastDirection, NAVIGATION_AXIS_DEFAULT_WEB_VIEW_BEHAVIOR,
                            sendEvent, contentDescription, false);
                        mLastDownEventHandled = false;
                    } else {
                        mLastDownEventHandled = true;
                    }
                    break;
                default:
                    Log.w(LOG_TAG, "Unknown action code: " + actionCode);
            }
        }

        return mLastDownEventHandled;
    }

    /**
     * Set the current navigation axis.
     *
     * @param axis The axis to set.
     * @param sendEvent Whether to send an accessibility event to
     *        announce the change.
     */
    private void setCurrentAxis(int axis, boolean sendEvent, String contentDescription) {
        mCurrentAxis = axis;
        if (sendEvent) {
            final AccessibilityEvent event = getPartialyPopulatedAccessibilityEvent(
                    AccessibilityEvent.TYPE_ANNOUNCEMENT);
            event.getText().add(String.valueOf(axis));
            event.setContentDescription(contentDescription);
            sendAccessibilityEvent(event);
        }
    }

    /**
     * Performs conditional transition one axis to another.
     *
     * @param fromAxis The axis which must be the current for the transition to occur.
     * @param toAxis The axis to which to transition.
     * @param sendEvent Flag if to send an event to announce successful transition.
     * @param contentDescription A description of the performed action.
     */
    private void performAxisTransition(int fromAxis, int toAxis, boolean sendEvent,
            String contentDescription) {
        if (mCurrentAxis == fromAxis) {
            setCurrentAxis(toAxis, sendEvent, contentDescription);
        }
    }

    boolean performAccessibilityAction(int action, Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
            case AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY: {
                final int direction = getDirectionForAction(action);
                final int axis = getAxisForGranularity(arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT));
                return traverseGivenAxis(direction, axis, true, null, true);
            }
            case AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT:
            case AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT: {
                final int direction = getDirectionForAction(action);
                // TODO: Add support for moving by object.
                final int axis = NAVIGATION_AXIS_SENTENCE;
                return traverseGivenAxis(direction, axis, true, null, true);
            }
            default:
                return false;
        }
    }

    /**
     * Returns the {@link WebView}-defined direction for the given
     * {@link AccessibilityNodeInfo}-defined action.
     *
     * @param action An accessibility action identifier.
     * @return A web view navigation direction.
     */
    private static int getDirectionForAction(int action) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT:
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                return NAVIGATION_DIRECTION_FORWARD;
            case AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT:
            case AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                return NAVIGATION_DIRECTION_BACKWARD;
            default:
                return -1;
        }
    }

    /**
     * Returns the {@link WebView}-defined axis for the given
     * {@link AccessibilityNodeInfo}-defined granularity.
     *
     * @param granularity An accessibility granularity identifier.
     * @return A web view navigation axis.
     */
    private static int getAxisForGranularity(int granularity) {
        switch (granularity) {
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
                return NAVIGATION_AXIS_CHARACTER;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD:
                return NAVIGATION_AXIS_WORD;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                return NAVIGATION_AXIS_SENTENCE;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH:
                // TODO: This should map to object once we implement it.
                return NAVIGATION_AXIS_SENTENCE;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE:
                return NAVIGATION_AXIS_DOCUMENT;
            default:
                return -1;
        }
    }

    /**
     * Traverse the document along the given navigation axis.
     *
     * @param direction The direction of traversal.
     * @param axis The axis along which to traverse.
     * @param sendEvent Whether to send an accessibility event to
     *        announce the change.
     * @param contentDescription A description of the performed action.
     */
    private boolean traverseGivenAxis(int direction, int axis, boolean sendEvent,
            String contentDescription, boolean sychronous) {
        final WebViewCore webViewCore = mWebView.getWebViewCore();
        if (webViewCore == null) {
            return false;
        }

        if (sendEvent) {
            final AccessibilityEvent event = getPartialyPopulatedAccessibilityEvent(
                    AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
            // The text will be set upon receiving the selection string.
            event.setContentDescription(contentDescription);
            mScheduledEvent = event;
            mScheduledToken++;
        }

        // if the axis is the default let WebView handle the event which will
        // result in cursor ring movement and selection of its content
        if (axis == NAVIGATION_AXIS_DEFAULT_WEB_VIEW_BEHAVIOR) {
            return false;
        }

        final SomeArgs args = SomeArgs.obtain();
        args.argi1 = direction;
        args.argi2 = axis;
        args.argi3 = mScheduledToken;

        // If we don't need synchronous results, just return true.
        if (!sychronous) {
            webViewCore.sendMessage(EventHub.MODIFY_SELECTION, args);
            return true;
        }

        final boolean callbackResult;

        synchronized (mCallbackLock) {
            mCallbackReceived = false;

            // Asynchronously changes the selection in WebView, which responds by
            // calling onSelectionStringChanged().
            webViewCore.sendMessage(EventHub.MODIFY_SELECTION, args);

            try {
                mCallbackLock.wait(MODIFY_SELECTION_TIMEOUT);
            } catch (InterruptedException e) {
                // Do nothing.
            }

            callbackResult = mCallbackResult;
        }

        return (mCallbackReceived && callbackResult);
    }

    /* package */ void onSelectionStringChangedWebCoreThread(
            final String selection, final int token) {
        synchronized (mCallbackLock) {
            mCallbackReceived = true;
            mCallbackResult = (selection != null);
            mCallbackLock.notifyAll();
        }

        // Managing state and sending events must take place on the UI thread.
        mWebViewInternal.post(new Runnable() {
            @Override
            public void run() {
                onSelectionStringChangedMainThread(selection, token);
            }
        });
    }

    private void onSelectionStringChangedMainThread(String selection, int token) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Selection string: " + selection);
        }

        if (token != mScheduledToken) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Selection string has incorrect token: " + token);
            }
            return;
        }

        mIsLastSelectionStringNull = (selection == null);

        final AccessibilityEvent event = mScheduledEvent;
        mScheduledEvent = null;

        if ((event != null) && (selection != null)) {
            event.getText().add(selection);
            event.setFromIndex(0);
            event.setToIndex(selection.length());
            sendAccessibilityEvent(event);
        }
    }

    /**
     * Sends an {@link AccessibilityEvent}.
     *
     * @param event The event to send.
     */
    private void sendAccessibilityEvent(AccessibilityEvent event) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Dispatching: " + event);
        }
        // accessibility may be disabled while waiting for the selection string
        AccessibilityManager accessibilityManager =
            AccessibilityManager.getInstance(mWebView.getContext());
        if (accessibilityManager.isEnabled()) {
            accessibilityManager.sendAccessibilityEvent(event);
        }
    }

    /**
     * @return An accessibility event whose members are populated except its
     *         text and content description.
     */
    private AccessibilityEvent getPartialyPopulatedAccessibilityEvent(int eventType) {
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        mWebViewInternal.onInitializeAccessibilityEvent(event);
        return event;
    }

    /**
     * Ensures that the Web content key bindings are loaded.
     */
    private void ensureWebContentKeyBindings() {
        if (sBindings.size() > 0) {
            return;
        }

        String webContentKeyBindingsString  = Settings.Secure.getString(
                mWebView.getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_WEB_CONTENT_KEY_BINDINGS);

        SimpleStringSplitter semiColonSplitter = new SimpleStringSplitter(';');
        semiColonSplitter.setString(webContentKeyBindingsString);

        while (semiColonSplitter.hasNext()) {
            String bindingString = semiColonSplitter.next();
            if (TextUtils.isEmpty(bindingString)) {
                Log.e(LOG_TAG, "Disregarding malformed Web content key binding: "
                        + webContentKeyBindingsString);
                continue;
            }
            String[] keyValueArray = bindingString.split("=");
            if (keyValueArray.length != 2) {
                Log.e(LOG_TAG, "Disregarding malformed Web content key binding: " + bindingString);
                continue;
            }
            try {
                long keyCodeAndModifiers = Long.decode(keyValueArray[0].trim());
                String[] actionStrings = keyValueArray[1].split(":");
                int[] actions = new int[actionStrings.length];
                for (int i = 0, count = actions.length; i < count; i++) {
                    actions[i] = Integer.decode(actionStrings[i].trim());
                }
                sBindings.add(new AccessibilityWebContentKeyBinding(keyCodeAndModifiers, actions));
            } catch (NumberFormatException nfe) {
                Log.e(LOG_TAG, "Disregarding malformed key binding: " + bindingString);
            }
        }
    }

    private boolean isEnterActionKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /**
     * Represents a web content key-binding.
     */
    private static final class AccessibilityWebContentKeyBinding {

        private static final int MODIFIERS_OFFSET = 32;
        private static final long MODIFIERS_MASK = 0xFFFFFFF00000000L;

        private static final int KEY_CODE_OFFSET = 0;
        private static final long KEY_CODE_MASK = 0x00000000FFFFFFFFL;

        private static final int ACTION_OFFSET = 24;
        private static final int ACTION_MASK = 0xFF000000;

        private static final int FIRST_ARGUMENT_OFFSET = 16;
        private static final int FIRST_ARGUMENT_MASK = 0x00FF0000;

        private static final int SECOND_ARGUMENT_OFFSET = 8;
        private static final int SECOND_ARGUMENT_MASK = 0x0000FF00;

        private static final int THIRD_ARGUMENT_OFFSET = 0;
        private static final int THIRD_ARGUMENT_MASK = 0x000000FF;

        private final long mKeyCodeAndModifiers;

        private final int [] mActionSequence;

        /**
         * @return The key code of the binding key.
         */
        public int getKeyCode() {
            return (int) ((mKeyCodeAndModifiers & KEY_CODE_MASK) >> KEY_CODE_OFFSET);
        }

        /**
         * @return The meta state of the binding key.
         */
        public int getModifiers() {
            return (int) ((mKeyCodeAndModifiers & MODIFIERS_MASK) >> MODIFIERS_OFFSET);
        }

        /**
         * @return The number of actions in the key binding.
         */
        public int getActionCount() {
            return mActionSequence.length;
        }

        /**
         * @param index The action for a given action <code>index</code>.
         */
        public int getAction(int index) {
            return mActionSequence[index];
        }

        /**
         * @param index The action code for a given action <code>index</code>.
         */
        public int getActionCode(int index) {
            return (mActionSequence[index] & ACTION_MASK) >> ACTION_OFFSET;
        }

        /**
         * @param index The first argument for a given action <code>index</code>.
         */
        public int getFirstArgument(int index) {
            return (mActionSequence[index] & FIRST_ARGUMENT_MASK) >> FIRST_ARGUMENT_OFFSET;
        }

        /**
         * @param index The second argument for a given action <code>index</code>.
         */
        public int getSecondArgument(int index) {
            return (mActionSequence[index] & SECOND_ARGUMENT_MASK) >> SECOND_ARGUMENT_OFFSET;
        }

        /**
         * @param index The third argument for a given action <code>index</code>.
         */
        public int getThirdArgument(int index) {
            return (mActionSequence[index] & THIRD_ARGUMENT_MASK) >> THIRD_ARGUMENT_OFFSET;
        }

        /**
         * Creates a new instance.
         * @param keyCodeAndModifiers The key for the binding (key and modifiers).
         * @param actionSequence The sequence of action for the binding.
         */
        public AccessibilityWebContentKeyBinding(long keyCodeAndModifiers, int[] actionSequence) {
            mKeyCodeAndModifiers = keyCodeAndModifiers;
            mActionSequence = actionSequence;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("modifiers: ");
            builder.append(getModifiers());
            builder.append(", keyCode: ");
            builder.append(getKeyCode());
            builder.append(", actions[");
            for (int i = 0, count = getActionCount(); i < count; i++) {
                builder.append("{actionCode");
                builder.append(i);
                builder.append(": ");
                builder.append(getActionCode(i));
                builder.append(", firstArgument: ");
                builder.append(getFirstArgument(i));
                builder.append(", secondArgument: ");
                builder.append(getSecondArgument(i));
                builder.append(", thirdArgument: ");
                builder.append(getThirdArgument(i));
                builder.append("}");
            }
            builder.append("]");
            return builder.toString();
        }
    }
}
