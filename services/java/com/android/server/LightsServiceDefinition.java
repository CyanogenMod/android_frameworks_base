package com.android.server;

public interface LightsServiceDefinition {
    LightDefinition getLight(int lightIdBattery);

    public interface LightDefinition {
        void setBrightness(int brightness);
        void setBrightness(int brightness, int mode);
        void setColor(int color);
        void setFlashing(int color, int mode, int onMS, int offMS);
        void pulse();
        void pulse(int color, int onMS);
        void notificationPulse(int color, int onMS, int offMS);
        void turnOff();
    }
}
