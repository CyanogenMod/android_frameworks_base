#ifndef SINE_SOURCE_H_

#define SINE_SOURCE_H_

#include <media/stagefright/MediaSource.h>

#if __cplusplus < 201103L && !defined(__GXX_EXPERIMENTAL_CXX0X__) && !defined(constexpr)
#define constexpr const
#endif

namespace android {

struct MediaBufferGroup;

struct SineSource : public MediaSource {
    SineSource(int32_t sampleRate, int32_t numChannels);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **out, const ReadOptions *options = NULL);

protected:
    virtual ~SineSource();

private:
    enum { kBufferSize = 8192 };
    static constexpr double kFrequency = 500.0;

    bool mStarted;
    int32_t mSampleRate;
    int32_t mNumChannels;
    size_t mPhase;

    MediaBufferGroup *mGroup;
};

}  // namespace android

#endif // SINE_SOURCE_H_
