/* Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
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

package android.webkit;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @hide This is only used by the browser. Manager for HTML5 video views.
 */
class HTML5VideoViewManager
{
    private WebViewClassic mWebView;
    private ArrayList<HTML5VideoViewProxy> mProxyList;
    private final Thread mUiThread;

    public HTML5VideoViewManager(WebViewClassic webView) {
        mWebView = webView;
        mUiThread = Thread.currentThread();
        mProxyList = new ArrayList<HTML5VideoViewProxy>();
    }

    public boolean registerProxy(HTML5VideoViewProxy proxy) {
        assert (mUiThread == Thread.currentThread());
        boolean result = mProxyList.add(proxy);
        return result;
    }

    public boolean unregisterProxy(HTML5VideoViewProxy proxy) {
        assert (mUiThread == Thread.currentThread());
        boolean result = mProxyList.remove(proxy);
        return result;
    }

    public void setBaseLayer(int layer) {
        assert (mUiThread == Thread.currentThread());
        Iterator<HTML5VideoViewProxy> iter = mProxyList.iterator();
        while (iter.hasNext()) {
            HTML5VideoViewProxy proxy = iter.next();
            proxy.setBaseLayer(layer);
        }
    }

    public void suspend() {
        assert (mUiThread == Thread.currentThread());
        Iterator<HTML5VideoViewProxy> iter = mProxyList.iterator();
        while (iter.hasNext()) {
            HTML5VideoViewProxy proxy = iter.next();
            proxy.suspend();
        }
    }

    public void pauseAndDispatch() {
        assert (mUiThread == Thread.currentThread());
        Iterator<HTML5VideoViewProxy> iter = mProxyList.iterator();
        while (iter.hasNext()) {
            HTML5VideoViewProxy proxy = iter.next();
            proxy.pauseAndDispatch();
        }
    }

    public void enterFullscreenVideo(int layerId, String url) {
        assert (mUiThread == Thread.currentThread());
        Iterator<HTML5VideoViewProxy> iter = mProxyList.iterator();
        while (iter.hasNext()) {
            HTML5VideoViewProxy proxy = iter.next();
            if (proxy.getVideoLayerId() == layerId)
                proxy.webkitEnterFullscreen();
            else
                proxy.pauseAndDispatch();
        }
    }

    public void exitFullscreenVideo() {
        assert (mUiThread == Thread.currentThread());
        Iterator<HTML5VideoViewProxy> iter = mProxyList.iterator();
        while (iter.hasNext()) {
            HTML5VideoViewProxy proxy = iter.next();
            proxy.webKitExitFullscreen();
        }
    }
}
