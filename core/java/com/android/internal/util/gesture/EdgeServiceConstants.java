/*
 * Copyright (C) 2013 The CyanogenMod Project (Jens Doll)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.internal.util.gesture;

/**
 * Constants needed for the edge gesture service.
 *
 * @see com.android.internal.util.gesture.EdgeGesturePosition
 */
public final class EdgeServiceConstants {

    private EdgeServiceConstants() {
        // no object allowed
    }

    /**
     * Mask for coding positions within the flags of
     * {@code updateEdgeGestureActivationListener()}.
     * <p>
     * Positions are specified by {@code EdgeGesturePosition.FLAG}.
     */
    public static final int POSITION_MASK = 0x0000001f;

    /**
     * Mask for coding sensitivity within the flags of
     * {@code updateEdgeGestureActivationListener()}.
     * <p>
     * Sensitivity influences the speed of the swipe, the trigger area, and trigger distance that
     * is needed to activate the edge gesture.
     */
    public static final int SENSITIVITY_MASK = 0x70000000;

    /**
     * Number of bits to shift left, to get a integer within the {@link #SENSITIVITY_MASK}.
     */
    public static final int SENSITIVITY_SHIFT = 28;

    /**
     * No sensitivity specified at all, the service may choose a sensitivity level on its own.
     */
    public static final int SENSITIVITY_NONE = 0;

    /**
     * Default sensitivity, picked by the edge gesture service automatically.
     */
    public static final int SENSITIVITY_DEFAULT = 2;

    /**
     * Lowest valid sensitivity value.
     */
    public static final int SENSITIVITY_LOWEST = 1;

    /**
     * Highest sensitivity value.
     */
    public static final int SENSITIVITY_HIGHEST = 4;

    /**
     * Do not cut 10% area on th edges
     */
    public static final int UNRESTRICTED = 0x10;

    /**
     * This listener does not likes enabling/disabling filter
     * because it interrupt in motion events.
     */
    public static final int LONG_LIVING = 0x20;

    /**
     * Allow IME to reduce left and right trigger height.
     */
    public static final int IME_CONTROL = 0x10;

}
