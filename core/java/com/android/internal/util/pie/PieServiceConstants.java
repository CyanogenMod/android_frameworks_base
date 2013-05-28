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
package com.android.internal.util.pie;

/**
 * Constants needed for the pie service.
 *
 * @see PiePosition
 */
public final class PieServiceConstants {

    private PieServiceConstants() {
        // no object allowed
    }

    /**
     * Mask for coding positions within the flags of
     * {@code updatePieActivationListener()}.
     * <p>
     * Positions are specified by {@code PiePosition.FLAG}.
     */
    public static final int POSITION_MASK = 0x0000000f;

    /**
     * Mask for coding sensitivity within the flags of
     * {@code updatePieActivationListener()}.
     * <p>
     * Sensitivity influences the speed of the swipe, the trigger area, and trigger distance that
     * is needed to activate the pie.
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
     * Default sensitivity, picked by the pie service automatically. 
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

}
