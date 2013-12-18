/*
 * Copyright (C) 2009 The Android Open Source Project
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

/**
 * This class is a container for all of the debug flags used in the Java
 * components of webkit.  These flags must be final in order to ensure that
 * the compiler optimizes the code that uses them out of the final executable.
 *
 * The name of each flags maps directly to the name of the class in which that
 * flag is used.
 *
 * @hide Only used by WebView implementations.
 */
public class DebugFlags {

    public static final boolean COOKIE_SYNC_MANAGER = false;
    public static final boolean TRACE_API = false;
    public static final boolean TRACE_CALLBACK = false;
    public static final boolean TRACE_JAVASCRIPT_BRIDGE = false;
    public static final boolean URL_UTIL = false;
    public static final boolean WEB_SYNC_MANAGER = false;

    // TODO: Delete these when WebViewClassic is moved
    public static final boolean BROWSER_FRAME = false;
    public static final boolean CACHE_MANAGER = false;
    public static final boolean CALLBACK_PROXY = false;
    public static final boolean COOKIE_MANAGER = false;
    public static final boolean FRAME_LOADER = false;
    public static final boolean J_WEB_CORE_JAVA_BRIDGE = false;// HIGHLY VERBOSE
    public static final boolean LOAD_LISTENER = false;
    public static final boolean MEASURE_PAGE_SWAP_FPS = false;
    public static final boolean NETWORK = false;
    public static final boolean SSL_ERROR_HANDLER = false;
    public static final boolean STREAM_LOADER = false;
    public static final boolean WEB_BACK_FORWARD_LIST = false;
    public static final boolean WEB_SETTINGS = false;
    public static final boolean WEB_VIEW = false;
    public static final boolean WEB_VIEW_CORE = false;


}
