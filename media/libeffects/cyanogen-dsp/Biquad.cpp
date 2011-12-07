/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "Biquad.h"
#include <math.h>

static int64_t toFixedPoint(float in) {
    return int64_t(0.5 + in * (int64_t(1) << 32));
}

Biquad::Biquad()
{
    reset();
    setCoefficients(0, 1, 0, 0, 1, 0, 0);
}

Biquad::~Biquad()
{
}

void Biquad::setCoefficients(int32_t steps, float a0, float a1, float a2, float b0, float b1, float b2)
{
    int64_t A1 = -toFixedPoint(a1/a0);
    int64_t A2 = -toFixedPoint(a2/a0);
    int64_t B0 = toFixedPoint(b0/a0);
    int64_t B1 = toFixedPoint(b1/a0);
    int64_t B2 = toFixedPoint(b2/a0);

    if (steps == 0) {
        mA1 = A1;
        mA2 = A2;
        mB0 = B0;
        mB1 = B1;
        mB2 = B2;
        mInterpolationSteps = 0;
    } else {
        mA1dif = (A1 - mA1) / steps;
        mA2dif = (A2 - mA2) / steps;
        mB0dif = (B0 - mB0) / steps;
        mB1dif = (B1 - mB1) / steps;
        mB2dif = (B2 - mB2) / steps;
        mInterpolationSteps = steps;
    }
}

void Biquad::reset()
{
    mInterpolationSteps = 0;
    mA1 = 0;
    mA2 = 0;
    mB0 = 0;
    mB1 = 0;
    mB2 = 0;
    mX1 = 0;
    mX2 = 0;
    mY1 = 0;
    mY2 = 0;
}

void Biquad::setHighShelf(int32_t steps, float center_frequency, float sampling_frequency, float gainDb, float slope, float overallGainDb)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, gainDb/40);
    float alpha = sinf(w0)/2 * sqrtf( (A + 1/A)*(1/slope - 1) + 2 );

    float b0 =    A*( (A+1) + (A-1)*cosf(w0) + 2*sqrtf(A)*alpha );
    float b1 = -2*A*( (A-1) + (A+1)*cosf(w0)                   );
    float b2 =    A*( (A+1) + (A-1)*cosf(w0) - 2*sqrtf(A)*alpha );
    float a0 =        (A+1) - (A-1)*cosf(w0) + 2*sqrtf(A)*alpha  ;
    float a1 =    2*( (A-1) - (A+1)*cosf(w0)                   );
    float a2 =        (A+1) - (A-1)*cosf(w0) - 2*sqrtf(A)*alpha  ;

    float overallGain = powf(10, overallGainDb / 20);
    b0 *= overallGain;
    b1 *= overallGain;
    b2 *= overallGain;

    setCoefficients(steps, a0, a1, a2, b0, b1, b2);
}

void Biquad::setBandPass(int32_t steps, float center_frequency, float sampling_frequency, float resonance)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float alpha = sinf(w0) / (2*resonance);

    float b0 =   sinf(w0)/2;
    float b1 =   0;
    float b2 =  -sinf(w0)/2;
    float a0 =   1 + alpha;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha;

    setCoefficients(steps, a0, a1, a2, b0, b1, b2);
}

void Biquad::setLowPass(int32_t steps, float center_frequency, float sampling_frequency, float resonance)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float alpha = sinf(w0) / (2*resonance);

    float b0 =  (1 - cosf(w0))/2;
    float b1 =   1 - cosf(w0);
    float b2 =  (1 - cosf(w0))/2;
    float a0 =   1 + alpha;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha;

    setCoefficients(steps, a0, a1, a2, b0, b1, b2);
}

int32_t Biquad::process(int32_t x0)
{
    int64_t y0 = mB0 * x0
        + mB1 * mX1
        + mB2 * mX2
        + mA1 * mY1
        + mA2 * mY2;
    y0 >>= 32;

    mY2 = mY1;
    mY1 = y0;

    mX2 = mX1;
    mX1 = x0;

    /* Interpolate biquad parameters */
    if (mInterpolationSteps != 0) {
        mInterpolationSteps --;
        mB0 += mB0dif;
        mB1 += mB1dif;
        mB2 += mB2dif;
        mA1 += mA1dif;
        mA2 += mA2dif;
    }

    return y0;
}
