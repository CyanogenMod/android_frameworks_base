#ifndef NU_HTTP_DATA_SOURCE_H_

#define NU_HTTP_DATA_SOURCE_H_

#include <media/stagefright/DataSource.h>
#include <utils/String8.h>
#include <utils/threads.h>

#include "HTTPStream.h"

namespace android {

struct NuHTTPDataSource : public DataSource {
    NuHTTPDataSource();

    status_t connect(
            const char *uri,
            const KeyedVector<String8, String8> *headers = NULL,
            off64_t offset = 0);

    void disconnect();

    virtual status_t initCheck() const;

    virtual ssize_t readAt(off64_t offset, void *data, size_t size);
    virtual status_t getSize(off64_t *size);
    virtual uint32_t flags();

protected:
    virtual ~NuHTTPDataSource();

private:
    enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    };

    Mutex mLock;

    State mState;

    String8 mHost;
    unsigned mPort;
    String8 mPath;
    String8 mHeaders;

    HTTPStream mHTTP;
    off64_t mOffset;
    off64_t mContentLength;
    bool mContentLengthValid;

    status_t connect(
            const char *uri, const String8 &headers, off64_t offset);

    status_t connect(
            const char *host, unsigned port, const char *path,
            const String8 &headers,
            off64_t offset);

    void applyTimeoutResponse();

    static void MakeFullHeaders(
            const KeyedVector<String8, String8> *overrides,
            String8 *headers);

    NuHTTPDataSource(const NuHTTPDataSource &);
    NuHTTPDataSource &operator=(const NuHTTPDataSource &);
};

}  // namespace android

#endif  // NU_HTTP_DATA_SOURCE_H_
