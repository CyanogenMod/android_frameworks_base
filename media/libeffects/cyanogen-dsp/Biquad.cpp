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
    setCoefficients(1, 0, 0, 0, 0, 0);
}

Biquad::~Biquad()
{
}

void Biquad::setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2)
{
    mA1 = -toFixedPoint(a1/a0);
    mA2 = -toFixedPoint(a2/a0);
    mB0 = toFixedPoint(b0/a0);
    mB1 = toFixedPoint(b1/a0);
    mB2 = toFixedPoint(b2/a0);
}

void Biquad::reset()
{
    mX1 = 0;
    mX2 = 0;
    mY1 = 0;
    mY2 = 0;
}

void Biquad::setHighShelf(float center_frequency, float sampling_frequency, float db_gain, float slope)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);
    float alpha = sinf(w0)/2 * sqrtf( (A + 1/A)*(1/slope - 1) + 2 );

    float b0 =    A*( (A+1) + (A-1)*cosf(w0) + 2*sqrtf(A)*alpha );
    float b1 = -2*A*( (A-1) + (A+1)*cosf(w0)                   );
    float b2 =    A*( (A+1) + (A-1)*cosf(w0) - 2*sqrtf(A)*alpha );
    float a0 =        (A+1) - (A-1)*cosf(w0) + 2*sqrtf(A)*alpha  ;
    float a1 =    2*( (A-1) - (A+1)*cosf(w0)                   );
    float a2 =        (A+1) - (A-1)*cosf(w0) - 2*sqrtf(A)*alpha  ;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void Biquad::setBandPass(float center_frequency, float sampling_frequency, float resonance)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float alpha = sinf(w0) / (2*resonance);

    float b0 =   sinf(w0)/2;
    float b1 =   0;
    float b2 =  -sinf(w0)/2;
    float a0 =   1 + alpha;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void Biquad::setLowPass(float center_frequency, float sampling_frequency, float resonance)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float alpha = sinf(w0) / (2*resonance);

    float b0 =  (1 - cosf(w0))/2;
    float b1 =   1 - cosf(w0);
    float b2 =  (1 - cosf(w0))/2;
    float a0 =   1 + alpha;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha;

    setCoefficients(a0, a1, a2, b0, b1, b2);
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

    return y0;
}
