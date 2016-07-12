package android.voice;


import com.android.internal.app.IVoiceInteractionManagerService;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;
import com.android.internal.app.IVoiceInteractor;

import android.app.Activity;
import android.app.VoiceInteractor;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

/**
 *
 * @hide
 */
public class VoiceManager {

    private final Context mContext;

    private final IVoiceInteractionManagerService mService;

    private final IBinder mIBinder;

    public VoiceManager(Context context, IVoiceInteractionManagerService service, IBinder ibinder) {
        mContext = context;
        mService = service;
        mIBinder = ibinder;
    }

    private class VoiceManagerCallback implements IVoiceInteractionSessionShowCallback {

        @Override
        public void onFailed() throws RemoteException {
            Log.v("BIRD", "failed");
        }

        @Override
        public void onShown() throws RemoteException {
            Log.v("BIRD", "shown");
        }

        @Override
        public IBinder asBinder() {
            Log.v("BIRD", "binded");
            return null;
        }
    }

    public VoiceInteractor getVoiceInteractorSession(IBinder token, Activity activity) {
        long identity = Binder.clearCallingIdentity();
        try {
            Bundle b = new Bundle();
            int flag = VoiceInteractionSession.SHOW_WITH_ASSIST;
            VoiceManagerCallback vc = new VoiceManagerCallback();
            IVoiceInteractor interactor = mService.requestCurrentSession(b, flag, vc, mIBinder);
            return new VoiceInteractor(interactor, activity, Looper.myLooper());
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

}
