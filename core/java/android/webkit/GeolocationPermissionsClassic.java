/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.os.Handler;
import android.os.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

// This class is the Java counterpart of the WebKit C++ GeolocationPermissions
// class. It simply marshals calls from the UI thread to the WebKit thread.
final class GeolocationPermissionsClassic extends GeolocationPermissions {
    private Handler mHandler;
    private Handler mUIHandler;

    // A queue to store messages until the handler is ready.
    private Vector<Message> mQueuedMessages;

    // Message ids
    static final int GET_ORIGINS = 0;
    static final int GET_ALLOWED = 1;
    static final int CLEAR = 2;
    static final int ALLOW = 3;
    static final int CLEAR_ALL = 4;

    // Message ids on the UI thread
    static final int RETURN_ORIGINS = 0;
    static final int RETURN_ALLOWED = 1;

    private static final String ORIGINS = "origins";
    private static final String ORIGIN = "origin";
    private static final String CALLBACK = "callback";
    private static final String ALLOWED = "allowed";

    // Global instance
    private static GeolocationPermissionsClassic sInstance;

    public static GeolocationPermissionsClassic getInstance() {
        if (sInstance == null) {
            sInstance = new GeolocationPermissionsClassic();
        }
        return sInstance;
      }

    /**
     * Creates the UI message handler. Must be called on the UI thread.
     * @hide
     */
    public void createUIHandler() {
        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    // Runs on the UI thread.
                    switch (msg.what) {
                        case RETURN_ORIGINS: {
                            Map values = (Map) msg.obj;
                            Set<String> origins = (Set<String>) values.get(ORIGINS);
                            ValueCallback<Set<String> > callback = (ValueCallback<Set<String> >) values.get(CALLBACK);
                            callback.onReceiveValue(origins);
                        } break;
                        case RETURN_ALLOWED: {
                            Map values = (Map) msg.obj;
                            Boolean allowed = (Boolean) values.get(ALLOWED);
                            ValueCallback<Boolean> callback = (ValueCallback<Boolean>) values.get(CALLBACK);
                            callback.onReceiveValue(allowed);
                        } break;
                    }
                }
            };
        }
    }

    /**
     * Creates the message handler. Must be called on the WebKit thread.
     * @hide
     */
    public synchronized void createHandler() {
        if (mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    // Runs on the WebKit thread.
                    switch (msg.what) {
                        case GET_ORIGINS: {
                            Set origins = nativeGetOrigins();
                            ValueCallback callback = (ValueCallback) msg.obj;
                            Map values = new HashMap<String, Object>();
                            values.put(CALLBACK, callback);
                            values.put(ORIGINS, origins);
                            postUIMessage(Message.obtain(null, RETURN_ORIGINS, values));
                            } break;
                        case GET_ALLOWED: {
                            Map values = (Map) msg.obj;
                            String origin = (String) values.get(ORIGIN);
                            ValueCallback callback = (ValueCallback) values.get(CALLBACK);
                            boolean allowed = nativeGetAllowed(origin);
                            Map retValues = new HashMap<String, Object>();
                            retValues.put(CALLBACK, callback);
                            retValues.put(ALLOWED, Boolean.valueOf(allowed));
                            postUIMessage(Message.obtain(null, RETURN_ALLOWED, retValues));
                            } break;
                        case CLEAR:
                            nativeClear((String) msg.obj);
                            break;
                        case ALLOW:
                            nativeAllow((String) msg.obj);
                            break;
                        case CLEAR_ALL:
                            nativeClearAll();
                            break;
                    }
                }
            };

            // Handle the queued messages
            if (mQueuedMessages != null) {
                while (!mQueuedMessages.isEmpty()) {
                    mHandler.sendMessage(mQueuedMessages.remove(0));
                }
                mQueuedMessages = null;
            }
        }
    }

    /**
     * Utility function to send a message to our handler.
     */
    private synchronized void postMessage(Message msg) {
        if (mHandler == null) {
            if (mQueuedMessages == null) {
                mQueuedMessages = new Vector<Message>();
            }
            mQueuedMessages.add(msg);
        } else {
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Utility function to send a message to the handler on the UI thread
     */
    private void postUIMessage(Message msg) {
        if (mUIHandler != null) {
            mUIHandler.sendMessage(msg);
        }
    }

    // Note that we represent the origins as strings. These are created using
    // WebCore::SecurityOrigin::toString(). As long as all 'HTML 5 modules'
    // (Database, Geolocation etc) do so, it's safe to match up origins based
    // on this string.
    @Override
    public void getOrigins(ValueCallback<Set<String> > callback) {
        if (callback != null) {
            if (WebViewCore.THREAD_NAME.equals(Thread.currentThread().getName())) {
                Set origins = nativeGetOrigins();
                callback.onReceiveValue(origins);
            } else {
                postMessage(Message.obtain(null, GET_ORIGINS, callback));
            }
        }
    }

    @Override
    public void getAllowed(String origin, ValueCallback<Boolean> callback) {
        if (callback == null) {
            return;
        }
        if (origin == null) {
            callback.onReceiveValue(null);
            return;
        }
        if (WebViewCore.THREAD_NAME.equals(Thread.currentThread().getName())) {
            boolean allowed = nativeGetAllowed(origin);
            callback.onReceiveValue(Boolean.valueOf(allowed));
        } else {
            Map values = new HashMap<String, Object>();
            values.put(ORIGIN, origin);
            values.put(CALLBACK, callback);
            postMessage(Message.obtain(null, GET_ALLOWED, values));
        }
    }

    // This method may be called before the WebKit
    // thread has intialized the message handler. Messages will be queued until
    // this time.
    @Override
    public void clear(String origin) {
        // Called on the UI thread.
        postMessage(Message.obtain(null, CLEAR, origin));
    }

    // This method may be called before the WebKit
    // thread has intialized the message handler. Messages will be queued until
    // this time.
    @Override
    public void allow(String origin) {
        // Called on the UI thread.
        postMessage(Message.obtain(null, ALLOW, origin));
    }

    @Override
    public void clearAll() {
        // Called on the UI thread.
        postMessage(Message.obtain(null, CLEAR_ALL));
    }

    GeolocationPermissionsClassic() {}

    // Native functions, run on the WebKit thread.
    private static native Set nativeGetOrigins();
    private static native boolean nativeGetAllowed(String origin);
    private static native void nativeClear(String origin);
    private static native void nativeAllow(String origin);
    private static native void nativeClearAll();
}
