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
 * Defines the positions in which pie controls may appear and gestures may be recognized by the
 * pie service.
 * This defines an index and an flag for each position.
 */
public enum PiePosition {
    LEFT(0, 0),
    BOTTOM(1, 1),
    RIGHT(2, 1),
    TOP(3, 0);

    PiePosition(int index, int factor) {
        INDEX = index;
        FLAG = (0x01<<index);
        FACTOR = factor;
    }

    public final int INDEX;
    public final int FLAG;
    /**
     * This is 1 when the position is not at the axis (like {@link PiePosition.RIGHT} is
     * at {@code Layout.getWidth()} not at {@code 0}).
     */
    public final int FACTOR;
}
