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

package android.net;

import android.content.Context;
import android.os.Handler;
import android.os.Messenger;

import com.android.internal.util.Preconditions;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface to control and observe state of a specific network, hiding
 * network-specific details from {@link ConnectivityManager}. Surfaces events
 * through the registered {@link Handler} to enable {@link ConnectivityManager}
 * to respond to state changes over time.
 *
 * @hide
 */
public abstract class BaseNetworkStateTracker implements NetworkStateTracker {
    // TODO: better document threading expectations
    // TODO: migrate to make NetworkStateTracker abstract class

    public static final String PROP_TCP_BUFFER_UNKNOWN = "net.tcp.buffersize.unknown";
    public static final String PROP_TCP_BUFFER_WIFI = "net.tcp.buffersize.wifi";
    public static final String PROP_TCP_DELACK_DEFAULT = "net.tcp.delack.default";
    public static final String PROP_TCP_USERCFG_DEFAULT = "net.tcp.usercfg.default";
    public static final String PROP_TCP_DELACK_WIFI = "net.tcp.delack.wifi";
    public static final String PROP_TCP_USERCFG_WIFI = "net.tcp.usercfg.wifi";
    public static final String PROP_TCP_DELACK_LTE = "net.tcp.delack.lte";
    public static final String PROP_TCP_USERCFG_LTE = "net.tcp.usercfg.lte";

    protected Context mContext;
    private Handler mTarget;

    protected NetworkInfo mNetworkInfo;
    protected LinkProperties mLinkProperties;
    protected LinkCapabilities mLinkCapabilities;

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    public BaseNetworkStateTracker(int networkType) {
        mNetworkInfo = new NetworkInfo(
                networkType, -1, ConnectivityManager.getNetworkTypeName(networkType), null);
        mLinkProperties = new LinkProperties();
        mLinkCapabilities = new LinkCapabilities();
    }

    protected BaseNetworkStateTracker() {
        // By default, let the sub classes construct everything
    }

    @Deprecated
    protected Handler getTargetHandler() {
        return mTarget;
    }

    protected final void dispatchStateChanged() {
        // TODO: include snapshot of other fields when sending
        mTarget.obtainMessage(EVENT_STATE_CHANGED, getNetworkInfo()).sendToTarget();
    }

    protected final void dispatchConfigurationChanged() {
        // TODO: include snapshot of other fields when sending
        mTarget.obtainMessage(EVENT_CONFIGURATION_CHANGED, getNetworkInfo()).sendToTarget();
    }

    @Override
    public void startMonitoring(Context context, Handler target) {
        mContext = Preconditions.checkNotNull(context);
        mTarget = Preconditions.checkNotNull(target);
        startMonitoringInternal();
    }

    protected void startMonitoringInternal() {

    }

    @Override
    public NetworkInfo getNetworkInfo() {
        return new NetworkInfo(mNetworkInfo);
    }

    @Override
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    @Override
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    @Override
    public LinkQualityInfo getLinkQualityInfo() {
        return null;
    }

    @Override
    public void captivePortalCheckComplete() {
        // not implemented
    }

    @Override
    public void captivePortalCheckCompleted(boolean isCaptivePortal) {
        // not implemented
    }

    @Override
    public boolean setRadio(boolean turnOn) {
        // Base tracker doesn't handle radios
        return true;
    }

    @Override
    public boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        // Base tracker doesn't handle enabled flags
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        // Base tracker doesn't handle enabled flags
    }

    @Override
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    @Override
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    @Override
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    @Override
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }

    @Override
    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }

    @Override
    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    @Override
    public void setDependencyMet(boolean met) {
        // Base tracker doesn't handle dependencies
    }

    @Override
    public void addStackedLink(LinkProperties link) {
        mLinkProperties.addStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link) {
        mLinkProperties.removeStackedLink(link);
    }

    @Override
    public void supplyMessenger(Messenger messenger) {
        // not supported on this network
    }

    @Override
    public String getNetworkInterfaceName() {
        if (mLinkProperties != null) {
            return mLinkProperties.getInterfaceName();
        } else {
            return null;
        }
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s) {
        // nothing to do
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s) {
        // nothing to do
    }

    // TCP delayed ack settings
    public String getTcpDelayedAckPropName() {
        return PROP_TCP_DELACK_DEFAULT;
    }

    public String getTcpUserConfigPropName() {
        return PROP_TCP_USERCFG_DEFAULT;
    }

    public String getDefaultTcpDelayedAckPropName() {
        return PROP_TCP_DELACK_DEFAULT;
    }

    public String getDefaultTcpUserConfigPropName() {
        return PROP_TCP_USERCFG_DEFAULT;
    }
}
