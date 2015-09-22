package android.hardware;

public abstract class ThermalListenerCallback extends IThermalListenerCallback.Stub {
    public static final class State {
        public static final int STATE_UNKNOWN = -1;
        public static final int STATE_COOL = 0;
        public static final int STATE_NORMAL = 1;
        public static final int STATE_HIGH = 2;
        public static final int STATE_EXTREME = 3;
        public static final String toString(int state) {
            switch (state) {
                case STATE_COOL:
                    return "STATE_COOL";
                case STATE_NORMAL:
                    return "STATE_NORMAL";
                case STATE_HIGH:
                    return "STATE_HIGH";
                case STATE_EXTREME:
                    return "STATE_EXTREME";
                default:
                    return "STATE_UNKNOWN";
            }
        }
    }
}