/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef HTTP_STREAM_H_

#define HTTP_STREAM_H_

#include <sys/types.h>

#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/MediaErrors.h>
#include <utils/KeyedVector.h>
#include <utils/threads.h>

namespace android {

class HTTPStream {
public:
    HTTPStream();
    ~HTTPStream();

    void setUID(uid_t uid);

    status_t connect(const char *server, int port = -1, bool https = false);
    status_t disconnect();

    status_t send(const char *data, size_t size);

    // Assumes data is a '\0' terminated string.
    status_t send(const char *data);

    // Receive up to "size" bytes of data.
    ssize_t receive(void *data, size_t size);

    status_t receive_header(int *http_status);

    // The header key used to retrieve the status line.
    static const char *kStatusKey;

    bool find_header_value(
            const AString &key, AString *value) const;

    // Pass a negative value to disable the timeout.
    void setReceiveTimeout(int seconds);

    // Receive a line of data terminated by CRLF, line will be '\0' terminated
    // _excluding_ the termianting CRLF.
    status_t receive_line(char *line, size_t size);

    static void RegisterSocketUser(int s, uid_t uid);

private:
    enum State {
        READY,
        CONNECTING,
        CONNECTED
    };

    State mState;
    Mutex mLock;

    bool mUIDValid;
    uid_t mUID;

    int mSocket;

    KeyedVector<AString, AString> mHeaders;

    void *mSSLContext;
    void *mSSL;

    HTTPStream(const HTTPStream &);
    HTTPStream &operator=(const HTTPStream &);
};

}  // namespace android

#endif  // HTTP_STREAM_H_
