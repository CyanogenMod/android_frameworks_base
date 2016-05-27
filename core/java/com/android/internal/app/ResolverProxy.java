package com.android.internal.app;

import java.util.List;
import android.content.pm.ResolveInfo;
import android.content.Context;
import android.content.Intent;
import android.widget.AbsListView;
import android.app.VoiceInteractor.PickOptionRequest.Option;
import com.android.internal.app.ResolverActivity.TargetInfo;

/** Relax access modifiers on key ResolverActivity extension methods to allow
    them to be overridden from a different package/classloader.
    Used by CMResolver */
public class ResolverProxy extends ResolverActivity {
    private static final String TAG = "ResolverProxy";

    /** If the superclass may set up adapter entries after onCreate completes,
        This method should be overridden to do nothing, and
        sendVoiceChoicesIfNeeded should be called once the adapter setup is
        complete. */
    @Override
    protected void onSetupVoiceInteraction() {
        super.onSetupVoiceInteraction();
    }

    /** see onSetupVoiceInteraction */
    @Override
    protected void sendVoiceChoicesIfNeeded() {
        super.sendVoiceChoicesIfNeeded();
    }

    @Override
    protected int getLayoutResource() {
        return super.getLayoutResource();
    }

    @Override
    protected void bindProfileView() {
        super.bindProfileView();
    }

    @Override
    protected Option optionForChooserTarget(TargetInfo target, int index) {
        return super.optionForChooserTarget(target, index);
    }

    @Override
    protected boolean shouldGetActivityMetadata() {
        return super.shouldGetActivityMetadata();
    }

    @Override
    protected boolean shouldAutoLaunchSingleChoice(TargetInfo target) {
        return super.shouldAutoLaunchSingleChoice(target);
    }

    @Override
    protected void showAppDetails(ResolveInfo ri) {
        super.showAppDetails(ri);
    }

    @Override
    void startSelected(int which, boolean always, boolean filtered) {
        super.startSelected(which, always, filtered);
    }

    @Override
    protected void onActivityStarted(TargetInfo cti) {
        super.onActivityStarted(cti);
    }

    @Override
    protected boolean configureContentView(
            List<Intent> payloadIntents, Intent[] initialIntents,
            List<ResolveInfo> rList, boolean alwaysUseOption) {
        return super.configureContentView(
                payloadIntents, initialIntents, rList, alwaysUseOption);
    }

    @Override
    protected void onPrepareAdapterView(
            AbsListView adapterView, ResolveListAdapter adapter, boolean alwaysUseOption) {
        super.onPrepareAdapterView(adapterView, adapter, alwaysUseOption);
    }

    /** subclasses cannot override this because ResolveListAdapter is an inaccessible
        type. Override _createAdapter(...) instead */
    @Override
    ResolveListAdapter createAdapter(Context context, List<Intent> payloadIntents,
            Intent[] initialIntents, List<ResolveInfo> rList, int launchedFromUid,
            boolean filterLastUsed) {
        ProxyListAdapter adapter = _createAdapter(
            context, payloadIntents, initialIntents, rList, launchedFromUid, filterLastUsed);
        return (adapter != null)
            ? adapter
            : super.createAdapter(context, payloadIntents, initialIntents,
                                  rList, launchedFromUid, filterLastUsed);
    }

    /** Subclasses should override this instead of createAdapter to avoid issues
        with ResolveListAdapter being an inaccessible type */
    protected ProxyListAdapter _createAdapter(Context context, List<Intent> payloadIntents,
            Intent[] initialIntents, List<ResolveInfo> rList, int launchedFromUid,
            boolean filterLastUsed) {
        return null;
    }

    /** Provides a visible type for exending ResolveListAdapter - fortunately the key
        methods one would need to override in ResolveListAdapter are all public or protected */
    public class ProxyListAdapter extends ResolveListAdapter {
        public ProxyListAdapter(
                Context context, List<Intent> payloadIntents, Intent[] initialIntents,
                List<ResolveInfo> rList, int launchedFromUid, boolean filterLastUsed) {
            super(context, payloadIntents, initialIntents, rList, launchedFromUid, filterLastUsed);
        }
    }
}
