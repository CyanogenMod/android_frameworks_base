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

#define LOG_TAG "DSP-entry"

#include <cutils/log.h>
#include <string.h>
#include <media/AudioEffect.h>
#include <media/EffectApi.h>
#include <media/EffectBassBoostApi.h>
#include <media/EffectEqualizerApi.h>
#include <media/EffectVirtualizerApi.h>

#include "Effect.h"
#include "EffectBassBoost.h"
#include "EffectCompression.h"
#include "EffectEqualizer.h"
#include "EffectVirtualizer.h"

/* Not available at media includes. */
static const effect_uuid_t SL_IID_VOLUME = {
	0x09e8ede0, 0xddde, 0x11db, 0xb4f6, 
	{ 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b }
};

static const effect_uuid_t CYANOGEN_COMPRESSION = {
	0xf27317f4, 0xc984, 0x4de6, 0x9a90,
	{ 0x54, 0x57, 0x59, 0x49, 0x5b, 0xf2 }
};

static effect_descriptor_t compression_descriptor = {
	SL_IID_VOLUME,
	CYANOGEN_COMPRESSION,
	EFFECT_API_VERSION,
	EFFECT_FLAG_INSERT_FIRST | EFFECT_FLAG_VOLUME_CTRL,
	10, /* 1 MIPS. FIXME: should be measured. */
	1,
	"CyanogenMod's Dynamic Range Compression",
	"Antti S. Lankila"
};

static const effect_uuid_t CYANOGEN_VIRTUALIZER = {
	0x7c6cc5f8, 0x6f34, 0x4449, 0xa282,
	{ 0xbe, 0xd8, 0x4f, 0x1a, 0x5b, 0x5a }
};

static effect_descriptor_t virtualizer_descriptor = {
	*SL_IID_VIRTUALIZER,
	CYANOGEN_VIRTUALIZER,
	EFFECT_API_VERSION,
	EFFECT_FLAG_INSERT_LAST,
	10, /* 1 MIPS. FIXME: should be measured. */
	1,
	"CyanogenMod's Headset Virtualization",
	"Antti S. Lankila"
};

static const effect_uuid_t CYANOGEN_EQUALIZER = {
       0x58bc9000, 0x0d7f, 0x462e, 0x90d2,
       { 0x03, 0x5e, 0xdd, 0xd8, 0xb4, 0x34 }
};

static effect_descriptor_t equalizer_descriptor = {
	*SL_IID_EQUALIZER,
	CYANOGEN_EQUALIZER,
	EFFECT_API_VERSION,
	0,
	10, /* 1 MIPS. FIXME: should be measured. */
	1,
	"CyanogenMod's Equalizer",
	"Antti S. Lankila"
};

static const effect_uuid_t CYANOGEN_BASSBOOST = {
	0x42b5cbf5, 0x4dd8, 0x4e79, 0xa5fb,
	{ 0xcc, 0xeb, 0x2c, 0xb5, 0x4e, 0x13 }
};

static effect_descriptor_t bassboost_descriptor = {
	*SL_IID_BASSBOOST,
	CYANOGEN_BASSBOOST,
	EFFECT_API_VERSION,
	0,
	10, /* 1 MIPS. FIXME: should be measured. */
	1,
	"CyanogenMod's Bass Boost",
	"Antti S. Lankila"
};

/* Library mandatory methods. */
extern "C" {

struct effect_module_s {
	const struct effect_interface_s *itfe;
	Effect *effect;
};

static int32_t generic_process(effect_interface_t self, audio_buffer_t *in, audio_buffer_t *out) {
	struct effect_module_s *e = (struct effect_module_s *) self;
	return e->effect->process(in, out);
}

static int32_t generic_command(effect_interface_t self, uint32_t cmdCode, uint32_t cmdSize, void *pCmdData, uint32_t *replySize, void *pReplyData) {
	struct effect_module_s *e = (struct effect_module_s *) self;
	return e->effect->command(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

static const struct effect_interface_s generic_interface = {
	generic_process,
	generic_command,
};

int32_t EffectQueryNumberEffects(uint32_t *num) {
	*num = 4;
	return 0;
}

int32_t EffectQueryEffect(uint32_t num, effect_descriptor_t *pDescriptor) {
	switch (num) {
	case 0:
		memcpy(pDescriptor, &compression_descriptor, sizeof(effect_descriptor_t));
		break;
	case 1:
		memcpy(pDescriptor, &equalizer_descriptor, sizeof(effect_descriptor_t));
		break;
	case 2:
		memcpy(pDescriptor, &virtualizer_descriptor, sizeof(effect_descriptor_t));
		break;
	case 3:
		memcpy(pDescriptor, &bassboost_descriptor, sizeof(effect_descriptor_t));
		break;
	default:
		return -ENOENT;
	}
	return 0;
}

int32_t EffectCreate(effect_uuid_t *uuid, int32_t sessionId, int32_t ioId, effect_interface_t *pEffect) {
	if (memcmp(uuid, &virtualizer_descriptor.uuid, sizeof(effect_uuid_t)) == 0) {
		struct effect_module_s *e = (struct effect_module_s *) calloc(1, sizeof(struct effect_module_s));
		e->itfe = &generic_interface;
		e->effect = new EffectVirtualizer();
		*pEffect = (effect_interface_t) e;
		return 0;
	}
	if (memcmp(uuid, &equalizer_descriptor.uuid, sizeof(effect_uuid_t)) == 0) {
		struct effect_module_s *e = (struct effect_module_s *) calloc(1, sizeof(struct effect_module_s));
		e->itfe = &generic_interface;
		e->effect = new EffectEqualizer();
		*pEffect = (effect_interface_t) e;
		return 0;
	}
	if (memcmp(uuid, &compression_descriptor.uuid, sizeof(effect_uuid_t)) == 0) {
		struct effect_module_s *e = (struct effect_module_s *) calloc(1, sizeof(struct effect_module_s));
		e->itfe = &generic_interface;
		e->effect = new EffectCompression();
		*pEffect = (effect_interface_t) e;
		return 0;
	}
	if (memcmp(uuid, &bassboost_descriptor.uuid, sizeof(effect_uuid_t)) == 0) {
		struct effect_module_s *e = (struct effect_module_s *) calloc(1, sizeof(struct effect_module_s));
		e->itfe = &generic_interface;
		e->effect = new EffectBassBoost();
		*pEffect = (effect_interface_t) e;
		return 0;
	}

	return -ENOENT;
}

int32_t EffectRelease(effect_interface_t ei) {
	struct effect_module_s *e = (struct effect_module_s *) ei;
	delete e->effect;
	free(e);
	return 0;
}

}
