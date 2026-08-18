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

import android.app.Application
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lineageos.settings.device.dac.utils.QuadDAC
import vendor.lge.hardware.audio.dac.control.V2_0.Feature

enum class BalanceSide { LEFT, RIGHT }

/**
 * Drives [QuadDACUiState] from the HIDL HAL. All HAL access goes through
 * [halMutex] + Dispatchers.IO so rapid control changes cannot interleave writes
 * or block the main thread (the old PreferenceFragment called the HAL
 * synchronously on the UI thread).
 */
class QuadDACViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(QuadDACUiState())
    val uiState: StateFlow<QuadDACUiState> = _uiState.asStateFlow()

    /** Serializes HAL writes so a burst of slider changes stays ordered. */
    private val halMutex = Mutex()

    /** Debounce jobs for slider-style controls (AVC + coefficients). */
    private var avcCommitJob: Job? = null
    private val coeffCommitJobs = arrayOfNulls<Job>(14)

    init {
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        val initialHeadset = getApplication<Application>()
            .getSystemService(AudioManager::class.java)?.isWiredHeadsetOn == true

        val result = withContext(Dispatchers.IO) {
            runCatching {
                QuadDAC.initialize()
                val features = QuadDAC.getSupportedFeatures().map { it.toInt() }.toSet()
                var state = QuadDACUiState(
                    serviceAvailable = true,
                    initializing = false,
                    headsetPlugged = initialHeadset,
                    supportedFeatures = features,
                )
                if (Feature.QuadDAC in features) {
                    state = state.copy(dacEnabled = QuadDAC.isEnabled())
                }
                if (Feature.SoundPreset in features) {
                    state = state.copy(soundPreset = QuadDAC.getSoundPreset())
                }
                if (Feature.DigitalFilter in features) {
                    state = state.copy(digitalFilter = QuadDAC.getDigitalFilter())
                }
                if (Feature.HifiMode in features) {
                    state = state.copy(hifiMode = QuadDAC.getDACMode())
                }
                if (Feature.AVCVolume in features) {
                    val range = QuadDAC.getSupportedFeatureValues(Feature.AVCVolume).range
                    state = state.copy(
                        avcVolume = QuadDAC.getAVCVolume(),
                        avcVolumeRange = range.min.toInt()..range.max.toInt(),
                    )
                }
                if (Feature.CustomFilter in features) {
                    state = state.copy(
                        customFilterShape = QuadDAC.getCustomFilterShape(),
                        customFilterSymmetry = QuadDAC.getCustomFilterSymmetry(),
                        coefficients = List(14) { QuadDAC.getCustomFilterCoeff(it) },
                    )
                }
                if (Feature.BalanceLeft in features && Feature.BalanceRight in features) {
                    val range = QuadDAC.getSupportedFeatureValues(Feature.BalanceLeft).range
                    state = state.copy(
                        balanceLeft = -QuadDAC.getLeftBalance(),
                        balanceRight = -QuadDAC.getRightBalance(),
                        balanceRange = range.min.toInt()..range.max.toInt(),
                    )
                }
                state
            }
        }
        _uiState.update {
            result.getOrElse { _ ->
                QuadDACUiState(
                    serviceAvailable = false,
                    initializing = false,
                    headsetPlugged = initialHeadset,
                )
            }
        }
    }

    fun onDacEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(dacEnabled = enabled, pendingOps = it.pendingOps + 1) }
            val ok = writeHal { if (enabled) QuadDAC.enable() else QuadDAC.disable() }
            _uiState.update { s ->
                if (ok) {
                    s.copy(pendingOps = s.pendingOps - 1)
                } else {
                    s.copy(dacEnabled = !enabled, pendingOps = s.pendingOps - 1,
                        errorEvent = s.errorEvent + 1)
                }
            }
        }
    }

    fun onHifiModeSelected(index: Int) = commitIndex(
        index,
        { s, v -> s.copy(hifiMode = v) },
        { QuadDAC.getDACMode() },
        { QuadDAC.setDACMode(it) },
    )

    fun onSoundPresetSelected(index: Int) = commitIndex(
        index,
        { s, v -> s.copy(soundPreset = v) },
        { QuadDAC.getSoundPreset() },
        { QuadDAC.setSoundPreset(it) },
    )

    fun onDigitalFilterSelected(index: Int) = commitIndex(
        index,
        { s, v -> s.copy(digitalFilter = v) },
        { QuadDAC.getDigitalFilter() },
        { QuadDAC.setDigitalFilter(it) },
    )

    fun onCustomFilterShapeSelected(index: Int) = commitIndex(
        index,
        { s, v -> s.copy(customFilterShape = v) },
        { QuadDAC.getCustomFilterShape() },
        { QuadDAC.setCustomFilterShape(it) },
    )

    fun onCustomFilterSymmetrySelected(index: Int) = commitIndex(
        index,
        { s, v -> s.copy(customFilterSymmetry = v) },
        { QuadDAC.getCustomFilterSymmetry() },
        { QuadDAC.setCustomFilterSymmetry(it) },
    )

    fun onAvcVolumeChanged(value: Int, commitNow: Boolean = false) {
        _uiState.update { it.copy(avcVolume = value) }
        avcCommitJob?.cancel()
        avcCommitJob = viewModelScope.launch {
            if (!commitNow) delay(SLIDER_DEBOUNCE_MS)
            val ok = writeHal { QuadDAC.setAVCVolume(value) }
            if (!ok) {
                val actual = withContext(Dispatchers.IO) {
                    runCatching { QuadDAC.getAVCVolume() }.getOrNull()
                }
                _uiState.update { s -> s.copy(
                    avcVolume = actual ?: s.avcVolume,
                    errorEvent = s.errorEvent + 1,
                ) }
            }
        }
    }

    fun onCoefficientChanged(index: Int, value: Int, commitNow: Boolean = false) {
        _uiState.update { s ->
            s.copy(coefficients = s.coefficients.toMutableList().also { it[index] = value })
        }
        coeffCommitJobs[index]?.cancel()
        coeffCommitJobs[index] = viewModelScope.launch {
            if (!commitNow) delay(SLIDER_DEBOUNCE_MS)
            val ok = writeHal { QuadDAC.setCustomFilterCoeff(index, value) }
            if (!ok) {
                val actual = withContext(Dispatchers.IO) {
                    runCatching { QuadDAC.getCustomFilterCoeff(index) }.getOrNull()
                }
                _uiState.update { s ->
                    val coeffs = s.coefficients.toMutableList()
                    if (actual != null) coeffs[index] = actual
                    s.copy(coefficients = coeffs, errorEvent = s.errorEvent + 1)
                }
            }
        }
    }

    fun onResetCoefficients() {
        viewModelScope.launch {
            val ok = writeHal { QuadDAC.resetCustomFilterCoeffs() }
            if (ok) {
                _uiState.update { it.copy(coefficients = List(14) { 0 }) }
            } else {
                _uiState.update { it.copy(errorEvent = it.errorEvent + 1) }
            }
        }
    }

    fun onBalanceAdjusted(side: BalanceSide, delta: Int) {
        viewModelScope.launch {
            val current = _uiState.value
            val currentValue =
                if (side == BalanceSide.LEFT) current.balanceLeft else current.balanceRight
            val newValue = (currentValue + delta).coerceIn(current.balanceRange)
            if (newValue == currentValue) return@launch  // at the boundary: no-op

            _uiState.update {
                if (side == BalanceSide.LEFT) it.copy(balanceLeft = newValue)
                else it.copy(balanceRight = newValue)
            }
            val ok = writeHal {
                if (side == BalanceSide.LEFT) QuadDAC.setLeftBalance(-newValue)
                else QuadDAC.setRightBalance(-newValue)
            }
            if (!ok) {
                val left = withContext(Dispatchers.IO) {
                    runCatching { QuadDAC.getLeftBalance() }.getOrNull()
                }
                val right = withContext(Dispatchers.IO) {
                    runCatching { QuadDAC.getRightBalance() }.getOrNull()
                }
                _uiState.update { s -> s.copy(
                    balanceLeft = left?.let { -it } ?: s.balanceLeft,
                    balanceRight = right?.let { -it } ?: s.balanceRight,
                    errorEvent = s.errorEvent + 1,
                ) }
            }
        }
    }

    fun onHeadsetStateChanged(plugged: Boolean) {
        _uiState.update { it.copy(headsetPlugged = plugged) }
    }

    /**
     * Re-reads headset state and DAC power on every (re)entry of the screen;
     * covers broadcasts missed while the screen was not visible.
     */
    fun refreshRuntimeState() {
        viewModelScope.launch {
            val headset = getApplication<Application>()
                .getSystemService(AudioManager::class.java)?.isWiredHeadsetOn == true
            val enabled = withContext(Dispatchers.IO) {
                if (_uiState.value.serviceAvailable) {
                    runCatching { QuadDAC.isEnabled() }.getOrNull()
                } else null
            }
            _uiState.update { s -> s.copy(
                headsetPlugged = headset,
                dacEnabled = enabled ?: s.dacEnabled,
            ) }
        }
    }

    /**
     * Optimistic update of a dropdown-style feature, then IO write; on failure
     * roll back from the HAL and raise the error event (old showApplyFailureToast).
     */
    private fun commitIndex(
        value: Int,
        update: (QuadDACUiState, Int) -> QuadDACUiState,
        read: () -> Int,
        write: (Int) -> Boolean,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingOps = it.pendingOps + 1) }
            val ok = writeHal { write(value) }
            if (ok) {
                _uiState.update { s -> update(s, value).copy(pendingOps = s.pendingOps - 1) }
            } else {
                val actual = withContext(Dispatchers.IO) {
                    runCatching { read() }.getOrNull()
                }
                _uiState.update { s -> update(s, actual ?: value).copy(
                    pendingOps = s.pendingOps - 1,
                    errorEvent = s.errorEvent + 1,
                ) }
            }
        }
    }

    private suspend fun writeHal(write: () -> Boolean): Boolean = halMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { write() }.getOrDefault(false)
        }
    }

    companion object {
        private const val SLIDER_DEBOUNCE_MS = 250L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                QuadDACViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!)
            }
        }
    }
}
