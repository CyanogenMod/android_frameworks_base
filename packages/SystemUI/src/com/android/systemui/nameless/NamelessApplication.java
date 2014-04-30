package com.android.systemui.nameless;

import android.app.Application;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;

@ReportsCrashes(
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formKey = "",
        formUri = "https://reports.nameless-rom" +
                ".org/acra-systemui/_design/acra-storage/_update/report",
        formUriBasicAuthLogin = "namelessreporter",
        formUriBasicAuthPassword = "weareopentoeveryone",
        mode = ReportingInteractionMode.SILENT)
public class NamelessApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ACRA.init(this);
    }
}
