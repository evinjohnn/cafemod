#include <jni.h>
#include <cstdint>
#include <vector>
#include <cmath>
#include <android/log.h>
#include <string.h>
#include <audio_effects/effect_c_api.h>  // Main effect interface header

#define TAG "CafeModeNative"

// Parameter IDs (must match AudioEffectWrapper.kt)
#define PARAM_SET_ENABLED           0
#define PARAM_SET_INTENSITY         1
#define PARAM_SET_SPATIAL_WIDTH     2

// Simple IIR filter for EQ
class SimpleIIR {
private:
    float x1 = 0, y1 = 0;
    float b0 = 1, b1 = 0, a1 = 0;

public:
    void setHighPass(float cutoff, float sampleRate) {
        if (sampleRate <= 0) return;
        float wc = tanf((float)M_PI * cutoff / sampleRate);
        float a0_inv = 1.0f / (1.0f + wc);
        b0 = a0_inv;
        b1 = -a0_inv;
        a1 = (1.0f - wc) * a0_inv;
    }

    void setLowPass(float cutoff, float sampleRate) {
        if (sampleRate <= 0) return;
        float wc = tanf((float)M_PI * cutoff / sampleRate);
        float a0_inv = 1.0f / (1.0f + wc);
        b0 = wc * a0_inv;
        b1 = wc * a0_inv;
        a1 = (1.0f - wc) * a0_inv;
    }

    float process(float in) {
        float y = b0 * in + b1 * x1 + a1 * y1;
        x1 = in;
        y1 = y;
        return y;
    }
};

// Main DSP state struct
typedef struct {
    bool isEnabled = false;
    float intensity = 0.5f;
    float spatialWidth = 0.5f;
    uint32_t sampleRate = 48000;

    std::vector<float> delayBufferL;
    std::vector<float> delayBufferR;
    int delayWritePos = 0;

    SimpleIIR hpFilterL, hpFilterR;
    SimpleIIR lpFilterL, lpFilterR;
} effect_state_t;


// Main DSP processing function
int32_t process_audio(effect_state_t *state, float *audio_buffer, int frame_count) {
    if (!state || !state->isEnabled) {
        return 0; // Bypass
    }

    int sample_count = frame_count * 2;
    float intensity = state->intensity;
    float width = state->spatialWidth;

    // Calculate dynamic parameters based on UI controls
    int haas_delay_samples = (int)((width * 0.015f) * state->sampleRate);
    if (state->delayBufferL.size() != (size_t)haas_delay_samples) {
        state->delayBufferL.assign(haas_delay_samples, 0.0f);
        state->delayBufferR.assign(haas_delay_samples, 0.0f);
        state->delayWritePos = 0;
    }

    float hp_cutoff = 50.0f + (intensity * 150.0f);
    float lp_cutoff = 12000.0f - (intensity * 8000.0f);
    state->hpFilterL.setHighPass(hp_cutoff, state->sampleRate); state->hpFilterR.setHighPass(hp_cutoff, state->sampleRate);
    state->lpFilterL.setLowPass(lp_cutoff, state->sampleRate);  state->lpFilterR.setLowPass(lp_cutoff, state->sampleRate);

    // Main Processing Loop
    for (int i = 0; i < sample_count; i += 2) {
        float inL = audio_buffer[i];
        float inR = audio_buffer[i+1];

        float eqL = state->lpFilterL.process(state->hpFilterL.process(inL));
        float eqR = state->lpFilterR.process(state->hpFilterR.process(inR));

        float delayedR = 0.0f;
        if (haas_delay_samples > 0) {
            delayedR = state->delayBufferR[state->delayWritePos];
            state->delayBufferR[state->delayWritePos] = eqR;
            state->delayWritePos = (state->delayWritePos + 1) % haas_delay_samples;
        }

        float widenedL = eqL;
        float widenedR = (haas_delay_samples > 0) ? delayedR : eqR;

        float outL = (inL * (1.0f - intensity)) + (widenedL * intensity);
        float outR = (inR * (1.0f - intensity)) + (widenedR * intensity);

        audio_buffer[i]     = outL;
        audio_buffer[i + 1] = outR;
    }

    return 0;
}


extern "C" {

// Effect descriptor
const effect_descriptor_t cafemode_descriptor = {
        {0xf2, 0x73, 0x17, 0xf4, 0xc9, 0x84, 0x4d, 0xe6, 0x9a, 0x90, 0x54, 0x57, 0x59, 0x49, 0x5b, 0xf2}, // Type UUID
        {0xf2, 0x59, 0x49, 0x54, 0xa4, 0x93, 0x47, 0x94, 0x82, 0xd6, 0x4a, 0x52, 0x76, 0x8b, 0x8a, 0xaf}, // Effect UUID
        EFFECT_CONTROL_API_VERSION,
        EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_FIRST | EFFECT_FLAG_PROCESS_INPLACE,
        0, 0, "CafeMode", "Judini"
};

int32_t cafemode_create(const effect_uuid_t *uuid, int32_t sessionId, int32_t ioId, void **pHandle) {
    *pHandle = new effect_state_t();
    return 0;
}

int32_t cafemode_release(void *handle) {
    delete static_cast<effect_state_t *>(handle);
    return 0;
}

int32_t cafemode_get_descriptor(const effect_uuid_t *uuid, effect_descriptor_t *pDescriptor) {
    if (memcmp(uuid, &cafemode_descriptor.uuid, sizeof(effect_uuid_t)) == 0) {
        memcpy(pDescriptor, &cafemode_descriptor, sizeof(effect_descriptor_t));
        return 0;
    }
    return -EINVAL;
}

int32_t cafemode_process(effect_handle_t self, audio_buffer_t *in, audio_buffer_t *out) {
    auto *state = static_cast<effect_state_t *>(self);
    if (!state) return -EINVAL;
    if (in->f32 != out->f32) memcpy(out->f32, in->f32, sizeof(float) * in->frameCount * 2);
    return process_audio(state, out->f32, out->frameCount);
}

int32_t cafemode_command(effect_handle_t self, uint32_t cmdCode, uint32_t cmdSize, void *pCmdData, uint32_t *replySize, void *pReplyData) {
    auto *state = static_cast<effect_state_t *>(self);
    if (!state) return -EINVAL;

    if (cmdCode == EFFECT_CMD_SET_CONFIG) {
        int32_t *data = (int32_t *)pCmdData;
        state->sampleRate = data[1];
        return 0;
    }

    if (cmdCode == PARAM_SET_ENABLED) if (pCmdData) state->isEnabled = *(int32_t *) pCmdData != 0;
    if (cmdCode == PARAM_SET_INTENSITY) if (pCmdData) state->intensity = *(float*)pCmdData;
    if (cmdCode == PARAM_SET_SPATIAL_WIDTH) if (pCmdData) state->spatialWidth = *(float*)pCmdData;

    return 0;
}

// Entry point for the Android audio framework
// --- AFTER ---
// Add this attribute to ensure the symbol is exported correctly.
extern "C" __attribute__((visibility("default")))
const audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
        .tag = AUDIO_EFFECT_LIBRARY_TAG,
        .version = EFFECT_LIBRARY_API_VERSION,
        .name = "CafeMode J.D",
        .implementor = "judini",
        .create_effect = cafemode_create,
        .release_effect = cafemode_release,
        .get_descriptor = cafemode_get_descriptor
};

} // extern "C"