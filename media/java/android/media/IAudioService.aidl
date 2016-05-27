/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.media;

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.media.AudioAttributes;
import android.media.AudioRoutesInfo;
import android.media.IAudioFocusDispatcher;
import android.media.IAudioRoutesObserver;
import android.media.IRemoteControlClient;
import android.media.IRemoteControlDisplay;
import android.media.IRemoteVolumeObserver;
import android.media.IRingtonePlayer;
import android.media.IVolumeController;
import android.media.Rating;
import android.media.VolumePolicy;
import android.media.audiopolicy.AudioPolicyConfig;
import android.media.audiopolicy.IAudioPolicyCallback;
import android.net.Uri;
import android.view.KeyEvent;

/**
 * {@hide}
 */
interface IAudioService {

    oneway void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags,
            String callingPackage, String caller);

    void adjustStreamVolume(int streamType, int direction, int flags, String callingPackage);

    void setStreamVolume(int streamType, int index, int flags, String callingPackage);

    oneway void setRemoteStreamVolume(int index);

    boolean isStreamMute(int streamType);

    void forceRemoteSubmixFullVolume(boolean startForcing, IBinder cb);

    boolean isMasterMute();

    void setMasterMute(boolean mute, int flags, String callingPackage, int userId);

    int getStreamVolume(int streamType);

    int getStreamMinVolume(int streamType);

    int getStreamMaxVolume(int streamType);

    void setStreamMaxVolume(int streamType, int maxVol);

    int getLastAudibleStreamVolume(int streamType);

    void setMicrophoneMute(boolean on, String callingPackage, int userId);

    void setRingerModeExternal(int ringerMode, String caller);

    void setRingerModeInternal(int ringerMode, String caller);

    int getRingerModeExternal();

    int getRingerModeInternal();

    boolean isValidRingerMode(int ringerMode);

    void setVibrateSetting(int vibrateType, int vibrateSetting);

    int getVibrateSetting(int vibrateType);

    boolean shouldVibrate(int vibrateType);

    void setMode(int mode, IBinder cb, String callingPackage);

    int getMode();

    oneway void playSoundEffect(int effectType);

    oneway void playSoundEffectVolume(int effectType, float volume);

    boolean loadSoundEffects();

    oneway void unloadSoundEffects();

    oneway void reloadAudioSettings();

    oneway void avrcpSupportsAbsoluteVolume(String address, boolean support);

    void setSpeakerphoneOn(boolean on);

    boolean isSpeakerphoneOn();

    void setBluetoothScoOn(boolean on);

    boolean isBluetoothScoOn();

    void setBluetoothA2dpOn(boolean on);

    boolean isBluetoothA2dpOn();

    int requestAudioFocus(in AudioAttributes aa, int durationHint, IBinder cb,
            IAudioFocusDispatcher fd, String clientId, String callingPackageName, int flags,
            IAudioPolicyCallback pcb);

    int abandonAudioFocus(IAudioFocusDispatcher fd, String clientId, in AudioAttributes aa);

    void unregisterAudioFocusClient(String clientId);

    int getCurrentAudioFocus();

    /**
     * Register an IRemoteControlDisplay.
     * Success of registration is subject to a check on
     *   the android.Manifest.permission.MEDIA_CONTENT_CONTROL permission.
     * Notify all IRemoteControlClient of the new display and cause the RemoteControlClient
     * at the top of the stack to update the new display with its information.
     * @param rcd the IRemoteControlDisplay to register. No effect if null.
     * @param w the maximum width of the expected bitmap. Negative or zero values indicate this
     *   display doesn't need to receive artwork.
     * @param h the maximum height of the expected bitmap. Negative or zero values indicate this
     *   display doesn't need to receive artwork.
     */
    boolean registerRemoteControlDisplay(in IRemoteControlDisplay rcd, int w, int h);

    /**
     * Like registerRemoteControlDisplay, but with success being subject to a check on
     *   the android.Manifest.permission.MEDIA_CONTENT_CONTROL permission, and if it fails,
     *   success is subject to listenerComp being one of the ENABLED_NOTIFICATION_LISTENERS
     *   components.
     */
    boolean registerRemoteController(in IRemoteControlDisplay rcd, int w, int h,
            in ComponentName listenerComp);

    /**
     * Unregister an IRemoteControlDisplay.
     * No effect if the IRemoteControlDisplay hasn't been successfully registered.
     * @param rcd the IRemoteControlDisplay to unregister. No effect if null.
     */
    oneway void unregisterRemoteControlDisplay(in IRemoteControlDisplay rcd);
    /**
     * Update the size of the artwork used by an IRemoteControlDisplay.
     * @param rcd the IRemoteControlDisplay with the new artwork size requirement
     * @param w the maximum width of the expected bitmap. Negative or zero values indicate this
     *   display doesn't need to receive artwork.
     * @param h the maximum height of the expected bitmap. Negative or zero values indicate this
     *   display doesn't need to receive artwork.
     */
    oneway void remoteControlDisplayUsesBitmapSize(in IRemoteControlDisplay rcd, int w, int h);
    /**
     * Controls whether a remote control display needs periodic checks of the RemoteControlClient
     * playback position to verify that the estimated position has not drifted from the actual
     * position. By default the check is not performed.
     * The IRemoteControlDisplay must have been previously registered for this to have any effect.
     * @param rcd the IRemoteControlDisplay for which the anti-drift mechanism will be enabled
     *     or disabled. Not null.
     * @param wantsSync if true, RemoteControlClient instances which expose their playback position
     *     to the framework will regularly compare the estimated playback position with the actual
     *     position, and will update the IRemoteControlDisplay implementation whenever a drift is
     *     detected.
     */
    oneway void remoteControlDisplayWantsPlaybackPositionSync(in IRemoteControlDisplay rcd,
            boolean wantsSync);

    void startBluetoothSco(IBinder cb, int targetSdkVersion);
    void startBluetoothScoVirtualCall(IBinder cb);
    void stopBluetoothSco(IBinder cb);

    void forceVolumeControlStream(int streamType, IBinder cb);

    void setRingtonePlayer(IRingtonePlayer player);
    IRingtonePlayer getRingtonePlayer();
    int getUiSoundsStreamType();

    void setWiredDeviceConnectionState(int type, int state, String address, String name,
            String caller);

    int setBluetoothA2dpDeviceConnectionState(in BluetoothDevice device, int state, int profile);

    AudioRoutesInfo startWatchingRoutes(in IAudioRoutesObserver observer);

    boolean isCameraSoundForced();

    void setVolumeController(in IVolumeController controller);

    void notifyVolumeControllerVisible(in IVolumeController controller, boolean visible);

    boolean isStreamAffectedByRingerMode(int streamType);

    boolean isStreamAffectedByMute(int streamType);

    void disableSafeMediaVolume(String callingPackage);

    int setHdmiSystemAudioSupported(boolean on);

    boolean isHdmiSystemAudioSupported();

    String registerAudioPolicy(in AudioPolicyConfig policyConfig,
            in IAudioPolicyCallback pcb, boolean hasFocusListener);

    oneway void unregisterAudioPolicyAsync(in IAudioPolicyCallback pcb);

    int setFocusPropertiesForPolicy(int duckingBehavior, in IAudioPolicyCallback pcb);

    void setVolumePolicy(in VolumePolicy policy);

    void setRemoteControlClientBrowsedPlayer();

    void getRemoteControlClientNowPlayingEntries();

    void setRemoteControlClientPlayItem(long uid, int scope);

    void updateRemoteControllerOnExistingMediaPlayers();

    void addMediaPlayerAndUpdateRemoteController(String packageName);

    void removeMediaPlayerAndUpdateRemoteController(String packageName);

    void handleHotwordInput(boolean listening);

    String getCurrentHotwordInputPackageName();

}
