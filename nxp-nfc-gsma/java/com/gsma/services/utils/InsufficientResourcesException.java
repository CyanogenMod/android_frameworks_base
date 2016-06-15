/*
 *
 *  Copyright (C) 2015 NXP Semiconductors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.gsma.services.utils;

/**
 * This class handles exception raised from the commit method when CLF routing table has no more free space.
 * @since NFCHST6.0
 */
@SuppressWarnings("serial")
public class InsufficientResourcesException extends Exception {

    /**
     * Constructs a new <code>InsufficientResourcesException</code> that includes the current stack trace.
     * @since NFCHST6.0
     */
    public InsufficientResourcesException() {};

    /**
     * Constructs a new <code>InsufficientResourcesException</code> with the current stack trace and the specified detail message.
     * @param detailMessage The detail message for this exception
     * @since NFCHST6.0
     */
    public InsufficientResourcesException(String detailMessage) {};

}
