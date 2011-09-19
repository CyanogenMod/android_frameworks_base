#pragma once

#include <stdint.h>

class Biquad {
    protected:
    int32_t mX1, mX2;
    int32_t mY1, mY2;
    int64_t mB0, mB1, mB2, mA1, mA2;
    int64_t mB0dif, mB1dif, mB2dif, mA1dif, mA2dif;
    int32_t mInterpolationSteps;

    void setCoefficients(int32_t steps, float a0, float a1, float a2, float b0, float b1, float b2);

    public:
    Biquad();
    virtual ~Biquad();
    void setHighShelf(int32_t steps, float cf, float sf, float gaindB, float slope, float overallGain);
    void setBandPass(int32_t steps, float cf, float sf, float resonance);
    void setLowPass(int32_t steps, float cf, float sf, float resonance);
    int32_t process(int32_t in);
    void reset();
};
