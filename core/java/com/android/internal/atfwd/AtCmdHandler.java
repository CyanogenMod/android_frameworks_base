/* Copyright (c) 2010,2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.atfwd;

public interface AtCmdHandler {
    public class AtCmdParseException extends Exception {
        private static final long serialVersionUID = 1L;

        public AtCmdParseException() {
            super();
        }

        public AtCmdParseException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public AtCmdParseException(String detailMessage) {
            super(detailMessage);
        }

        public AtCmdParseException(Throwable throwable) {
            super(throwable);
        }
    }

    public class AtCmdHandlerInstantiationException extends Exception {

        private static final long serialVersionUID = 1L;

        public AtCmdHandlerInstantiationException() {
            super();
        }

        public AtCmdHandlerInstantiationException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public AtCmdHandlerInstantiationException(String detailMessage) {
            super(detailMessage);
        }

        public AtCmdHandlerInstantiationException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class PauseEvent {
        private long mTime;
        public PauseEvent(long time) {
            mTime = time;
        }
        public long getTime() {
            return mTime;
        }
    }

    public String getCommandName();
    public AtCmdResponse handleCommand(AtCmd cmd);
}
