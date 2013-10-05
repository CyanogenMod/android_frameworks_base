/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
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

package android.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to be used when adding new features that will be
 * tested out in the ChaOS Lab prior to be a permanent feature.
 *
 * Example: @ChaosLab(name="SomeSnazzyFeature", classification=Classification.NEW_CLASS)
 * @hide
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ChaosLab
{
    /**
     * Name should be unique and consistent so it can be easily searched
     */
    String name();

    /**
     * Various classifications for annotating what is being added or changed
     */
    Classification classification();

    /**
     * Any additional information that can be useful.
     */
    String notes() default "";

    public static enum Classification
    {
        CHANGE_ACCESS,
        CHANGE_CODE,
        CHANGE_CODE_AND_ACCESS,
        CHANGE_PARAMETER,
        CHANGE_PARAMATER_AND_ACCESS,
        CHANGE_BASE_CLASS,
        NEW_CLASS,
        NEW_FIELD,
        NEW_METHOD;
    }
}
