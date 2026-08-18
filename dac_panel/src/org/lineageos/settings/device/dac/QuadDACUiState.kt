/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.settings.device.dac

import vendor.lge.hardware.audio.dac.control.V2_0.Feature

/**
 * Immutable snapshot of the QuadDAC panel state, driven by the HAL via QuadDACViewModel.
 *
 * Balance values are stored in display units where 1 unit = 0.5 dB; the HAL expects the
 * negated value (see QuadDAC#setLeftBalance / #setRightBalance).
 */
data class QuadDACUiState(
    /** HAL service reachable; false after QuadDAC.initialize() failed. */
    val serviceAvailable: Boolean = false,
    /** True while the first HAL init (features + current values) is in flight. */
    val initializing: Boolean = true,
    /** Wired headset plugged in (AudioManager + ACTION_HEADSET_PLUG). */
    val headsetPlugged: Boolean = false,
    /** DAC powered on per HAL isEnabled(); stays true when the headset is unplugged. */
    val dacEnabled: Boolean = false,
    /** In-flight HAL writes; > 0 disables the controls to prevent a write storm. */
    val pendingOps: Int = 0,
    /** Monotonic error counter; the UI surfaces one toast per increment. */
    val errorEvent: Long = 0L,
    /** Feature bitmask as reported by QuadDAC#getSupportedFeatures(). */
    val supportedFeatures: Set<Int> = emptySet(),
    val hifiMode: Int = 0,
    val soundPreset: Int = 0,
    val digitalFilter: Int = 0,
    val customFilterShape: Int = 0,
    val customFilterSymmetry: Int = 0,
    /** AVC volume in dB, range from HAL FeatureStates.range. */
    val avcVolume: Int = -14,
    val avcVolumeRange: IntRange = -24..0,
    /** Custom filter coefficients 0..13 (displayed as 0.X). */
    val coefficients: List<Int> = List(14) { 0 },
    val balanceLeft: Int = 0,
    val balanceRight: Int = 0,
    /** Balance range (per side) from HAL FeatureStates.range of Feature.BalanceLeft. */
    val balanceRange: IntRange = 0..0,
) {
    val dacSwitchEnabled: Boolean
        get() = headsetPlugged && serviceAvailable && pendingOps == 0

    /** Replacement for the old enableExtraSettings() gate. */
    val extraEnabled: Boolean
        get() = dacEnabled && headsetPlugged && serviceAvailable

    val dacSupported: Boolean
        get() = Feature.QuadDAC in supportedFeatures
    val soundPresetSupported: Boolean
        get() = Feature.SoundPreset in supportedFeatures
    val digitalFilterSupported: Boolean
        get() = Feature.DigitalFilter in supportedFeatures
    val hifiModeSupported: Boolean
        get() = Feature.HifiMode in supportedFeatures
    val avcSupported: Boolean
        get() = Feature.AVCVolume in supportedFeatures
    val customFilterSupported: Boolean
        get() = Feature.CustomFilter in supportedFeatures
    val balanceSupported: Boolean
        get() = Feature.BalanceLeft in supportedFeatures &&
                Feature.BalanceRight in supportedFeatures

    /** Replacement for checkCustomFilterVisibility(): only with filter [3] selected. */
    val customFilterVisible: Boolean
        get() = customFilterSupported && digitalFilter == 3
}
