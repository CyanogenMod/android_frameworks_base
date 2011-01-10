#pragma once

#include <stdint.h>

class Biquad {
    protected:
    int32_t mX1, mX2;
    int32_t mY1, mY2;
    int64_t mB0, mB1, mB2, mA1, mA2;

    void setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2);

    public:
    Biquad();
    virtual ~Biquad();
    void setHighShelf(float cf, float sf, float gain, float slope);
    void setBandPass(float cf, float sf, float resonance);
    void setLowPass(float cf, float sf, float resonance);
    int32_t process(int32_t in);
    void reset();
};
