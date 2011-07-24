//#define LOG_NDEBUG 0
#define LOG_TAG "StagefrightPlayer"
#include <utils/Log.h>

#include "StagefrightPlayer.h"

#include "AwesomePlayer.h"

#include <media/Metadata.h>
#include <media/stagefright/MediaExtractor.h>

namespace android {

StagefrightPlayer::StagefrightPlayer()
    : mPlayer(new AwesomePlayer) {
    LOGV("StagefrightPlayer");

    mPlayer->setListener(this);
}

StagefrightPlayer::~StagefrightPlayer() {
    LOGV("~StagefrightPlayer");
    reset();

    delete mPlayer;
    mPlayer = NULL;
}

status_t StagefrightPlayer::initCheck() {
    LOGV("initCheck");
    return OK;
}

status_t StagefrightPlayer::setDataSource(
        const char *url, const KeyedVector<String8, String8> *headers) {
    LOGI("setDataSource('%s')", url);
    return mPlayer->setDataSource(url, headers);
}

// Warning: The filedescriptor passed into this method will only be valid until
// the method returns, if you want to keep it, dup it!
status_t StagefrightPlayer::setDataSource(int fd, int64_t offset, int64_t length) {
    LOGV("setDataSource(%d, %lld, %lld)", fd, offset, length);
    return mPlayer->setDataSource(dup(fd), offset, length);
}

status_t StagefrightPlayer::setVideoSurface(const sp<ISurface> &surface) {
    LOGV("setVideoSurface");

    mPlayer->setISurface(surface);
    return OK;
}

status_t StagefrightPlayer::prepare() {
    return mPlayer->prepare();
}

status_t StagefrightPlayer::prepareAsync() {
    return mPlayer->prepareAsync();
}

status_t StagefrightPlayer::start() {
    LOGV("start");

    return mPlayer->play();
}

status_t StagefrightPlayer::stop() {
    LOGV("stop");

    return pause();  // what's the difference?
}

status_t StagefrightPlayer::pause() {
    LOGV("pause");

    return mPlayer->pause();
}

bool StagefrightPlayer::isPlaying() {
    LOGV("isPlaying");
    return mPlayer->isPlaying();
}

status_t StagefrightPlayer::seekTo(int msec) {
    LOGV("seekTo");

    status_t err = mPlayer->seekTo((int64_t)msec * 1000);

    return err;
}

status_t StagefrightPlayer::getCurrentPosition(int *msec) {
    LOGV("getCurrentPosition");

    int64_t positionUs;
    status_t err = mPlayer->getPosition(&positionUs);

    if (err != OK) {
        return err;
    }

    *msec = (positionUs + 500) / 1000;

    return OK;
}

status_t StagefrightPlayer::getDuration(int *msec) {
    LOGV("getDuration");

    int64_t durationUs;
    status_t err = mPlayer->getDuration(&durationUs);

    if (err != OK) {
        *msec = 0;
        return OK;
    }

    *msec = (durationUs + 500) / 1000;

    return OK;
}

status_t StagefrightPlayer::reset() {
    LOGV("reset");

    mPlayer->reset();

    return OK;
}

status_t StagefrightPlayer::setLooping(int loop) {
    LOGV("setLooping");

    return mPlayer->setLooping(loop);
}

player_type StagefrightPlayer::playerType() {
    LOGV("playerType");
    return STAGEFRIGHT_PLAYER;
}

status_t StagefrightPlayer::suspend() {
    LOGV("suspend");
    return mPlayer->suspend();
}

status_t StagefrightPlayer::resume() {
    LOGV("resume");
    return mPlayer->resume();
}

#ifdef OMAP_ENHANCEMENT
status_t StagefrightPlayer::requestVideoCloneMode(bool enable) {
    return mPlayer->requestVideoCloneMode(enable);
}
#endif

status_t StagefrightPlayer::invoke(const Parcel &request, Parcel *reply) {
    return INVALID_OPERATION;
}

void StagefrightPlayer::setAudioSink(const sp<AudioSink> &audioSink) {
    MediaPlayerInterface::setAudioSink(audioSink);

    mPlayer->setAudioSink(audioSink);
}

status_t StagefrightPlayer::getMetadata(
        const media::Metadata::Filter& ids, Parcel *records) {
    using media::Metadata;

    uint32_t flags = mPlayer->flags();

    Metadata metadata(records);

    metadata.appendBool(
            Metadata::kPauseAvailable,
            flags & MediaExtractor::CAN_PAUSE);

    metadata.appendBool(
            Metadata::kSeekBackwardAvailable,
            flags & MediaExtractor::CAN_SEEK_BACKWARD);

    metadata.appendBool(
            Metadata::kSeekForwardAvailable,
            flags & MediaExtractor::CAN_SEEK_FORWARD);

    metadata.appendBool(
            Metadata::kSeekAvailable,
            flags & MediaExtractor::CAN_SEEK);

    return OK;
}

}  // namespace android
