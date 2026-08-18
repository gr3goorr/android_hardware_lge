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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import org.lineageos.settings.device.dac.utils.Constants

@Composable
fun QuadDACPanelTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialTheme(
        // Follow the system (Android 12+ dynamic color from wallpaper); material3
        // falls back to the baseline scheme on older releases.
        colorScheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuadDACPanelScreen(
    initialSearchKey: String?,
    onBack: () -> Unit,
    viewModel: QuadDACViewModel = viewModel(factory = QuadDACViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Replacement for the old Fragment onResume/onPause receiver lifecycle.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                    val plugged = intent.getIntExtra("state", -1) == 1
                    viewModel.onHeadsetStateChanged(plugged)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_HEADSET_PLUG),
            ContextCompat.RECEIVER_EXPORTED,
        )
        viewModel.refreshRuntimeState()
        onDispose { context.unregisterReceiver(receiver) }
    }

    // One toast per failed HAL write (old showApplyFailureToast).
    LaunchedEffect(state.errorEvent) {
        if (state.errorEvent > 0L) {
            Toast.makeText(context, R.string.failed_to_apply_setting, Toast.LENGTH_SHORT).show()
        }
    }

    if (initialSearchKey != null) {
        Log.d(TAG, "Launched from search with key: ${initialSearchKey}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quad_dac)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.initializing -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !state.serviceAvailable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.quad_dac_unavail))
                }
            }
            else -> QuadDACPanelList(state, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun QuadDACPanelList(state: QuadDACUiState, viewModel: QuadDACViewModel,
        modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item(key = "dac_switch") {
            SectionTitle(stringResource(R.string.quad_dac_settings))
            SettingRow(title = stringResource(R.string.quad_dac)) {
                Switch(
                    checked = state.dacEnabled,
                    enabled = state.dacSwitchEnabled,
                    onCheckedChange = viewModel::onDacEnabledChanged,
                )
            }
        }
        if (state.hifiModeSupported) {
            item(key = Constants.HIFI_MODE_KEY) {
                DropdownSettingRow(
                    title = stringResource(R.string.hifi_mode),
                    options = entriesFor(Constants.HIFI_MODE_KEY),
                    selectedIndex = state.hifiMode,
                    enabled = state.extraEnabled,
                    onSelected = viewModel::onHifiModeSelected,
                )
            }
        }
        if (state.avcSupported) {
            item(key = Constants.AVC_VOLUME_KEY) {
                SliderSettingRow(
                    title = stringResource(R.string.avc_volume),
                    valueText = stringResource(R.string.avc_value_db, state.avcVolume),
                    value = state.avcVolume.toFloat(),
                    range = state.avcVolumeRange.first.toFloat()..state.avcVolumeRange.last.toFloat(),
                    enabled = state.extraEnabled,
                    onValueChange = viewModel::onAvcVolumeChanged,
                )
            }
        }
        if (state.soundPresetSupported) {
            item(key = Constants.SOUND_PRESET_KEY) {
                DropdownSettingRow(
                    title = stringResource(R.string.sound_preset),
                    options = entriesFor(Constants.SOUND_PRESET_KEY),
                    selectedIndex = state.soundPreset,
                    enabled = state.extraEnabled,
                    onSelected = viewModel::onSoundPresetSelected,
                )
            }
        }
        if (state.digitalFilterSupported) {
            item(key = Constants.DIGITAL_FILTER_KEY) {
                DropdownSettingRow(
                    title = stringResource(R.string.digital_filter),
                    options = entriesFor(Constants.DIGITAL_FILTER_KEY),
                    selectedIndex = state.digitalFilter,
                    enabled = state.extraEnabled,
                    onSelected = viewModel::onDigitalFilterSelected,
                )
            }
        }
        if (state.customFilterVisible) {
            item(key = "custom_filter_section") {
                SectionTitle(stringResource(R.string.customizable_filter_settings))
            }
            item(key = Constants.CUSTOM_FILTER_SHAPE_KEY) {
                DropdownSettingRow(
                    title = stringResource(R.string.cf_shape),
                    options = entriesFor(Constants.CUSTOM_FILTER_SHAPE_KEY),
                    selectedIndex = state.customFilterShape,
                    enabled = state.extraEnabled,
                    onSelected = viewModel::onCustomFilterShapeSelected,
                )
            }
            item(key = Constants.CUSTOM_FILTER_SYMMETRY_KEY) {
                DropdownSettingRow(
                    title = stringResource(R.string.cf_symmetry),
                    options = entriesFor(Constants.CUSTOM_FILTER_SYMMETRY_KEY),
                    selectedIndex = state.customFilterSymmetry,
                    enabled = state.extraEnabled,
                    onSelected = viewModel::onCustomFilterSymmetrySelected,
                )
            }
            item(key = "coeff_section") {
                SectionTitle(stringResource(R.string.customizable_filter_coeffs))
                Text(
                    text = stringResource(R.string.customizable_filter_coeffs_summary),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(
                state.coefficients,
                key = { index, _ -> Constants.CUSTOM_FILTER_COEFF_KEYS[index] },
            ) { index, value ->
                // 0.xxxxx for positive, -0.xxxxx for negative (sign before the dot)
                val coeffText = if (value < 0) "-0.${-value}" else "0.$value"
                CoefficientSettingRow(
                    title = stringResource(R.string.cf_coeff_label, index, coeffText),
                    value = value,
                    range = COEFF_RANGE_FIRST..COEFF_RANGE_LAST,
                    enabled = state.extraEnabled,
                    onValueChange = { newValue, commitNow ->
                        viewModel.onCoefficientChanged(index, newValue, commitNow)
                    },
                )
            }
            item(key = Constants.RESET_COEFFICIENTS_KEY) {
                OutlinedButton(
                    onClick = viewModel::onResetCoefficients,
                    enabled = state.extraEnabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp,
                        vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.cf_reset))
                }
            }
        }
        if (state.balanceSupported) {
            item(key = Constants.BALANCE_KEY) {
                SectionTitle(stringResource(R.string.quad_dac_balance))
                Text(
                    text = stringResource(R.string.quad_dac_balance_summary),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    BalanceColumn(
                        title = "L",
                        value = state.balanceLeft,
                        range = state.balanceRange,
                        enabled = state.extraEnabled,
                        onAdjust = { delta -> viewModel.onBalanceAdjusted(BalanceSide.LEFT, delta) },
                    )
                    BalanceColumn(
                        title = "R",
                        value = state.balanceRight,
                        range = state.balanceRange,
                        enabled = state.extraEnabled,
                        onAdjust = { delta -> viewModel.onBalanceAdjusted(BalanceSide.RIGHT, delta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun DropdownSettingRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.getOrNull(selectedIndex) ?: options.firstOrNull() ?: title

    SettingRow(title = title) {
        // The DropdownMenu must share a Box with its trigger: the menu's Popup
        // anchors to the nearest parent layout, which without the Box is the
        // full-width SettingRow — popping the menu at the screen's left edge.
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    valueText: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Int, Boolean) -> Unit,
) {
    // onValueChangeFinished carries no value, so track the latest drag position here.
    var latestValue by remember { mutableStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            if (valueText != null) {
                Text(text = valueText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = {
                latestValue = it
                onValueChange(it.roundToInt(), false)
            },
            onValueChangeFinished = { onValueChange(latestValue.roundToInt(), true) },
            valueRange = range,
            enabled = enabled,
        )
    }
}

/**
 * Coefficient row: slider for dragging plus a numeric text field for direct
 * entry. Input is committed on IME Done or focus loss; out-of-range values are
 * clamped, non-numeric input reverts to the current HAL value.
 */
@Composable
private fun CoefficientSettingRow(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var latestValue by remember { mutableStateOf(value) }
    // Mirror external updates (slider drags, HAL rollbacks) unless mid-edit.
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) {
            text = value.toString()
        }
    }
    val focusManager = LocalFocusManager.current
    val commit = {
        val parsed = text.trim().toIntOrNull()
        if (parsed == null) {
            text = value.toString()        // not a number: revert
        } else {
            val clamped = parsed.coerceIn(range.first, range.last)
            text = clamped.toString()
            onValueChange(clamped, true)
        }
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    // Digits only, with an optional single leading '-'; at most
                    // 7 digits caps the value at ±9999999. Bare "-" is allowed
                    // as an intermediate state while typing.
                    val digits = raw.filter { it in '0'..'9' }.take(7)
                    val cleaned = if (raw.startsWith('-')) "-$digits" else digits
                    val parsed = cleaned.toIntOrNull()
                    if (cleaned.isEmpty() || cleaned == "-"
                        || (parsed != null && parsed in range)) {
                        text = cleaned
                    }
                },
                modifier = Modifier.width(96.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                enabled = enabled,
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = {
                latestValue = it.roundToInt()
                onValueChange(latestValue, false)
            },
            onValueChangeFinished = { onValueChange(latestValue, true) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun BalanceColumn(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onAdjust(-1) },
                enabled = enabled && value > range.first,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.balance_value_db, value / 2.0),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = { onAdjust(+1) },
                enabled = enabled && value < range.last,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

/** Resolves the arrays.xml entries array for a preference key (index-aligned with HAL values). */
@Composable
private fun entriesFor(key: String): List<String> {
    val context = LocalContext.current
    val arrayRes = when (key) {
        Constants.HIFI_MODE_KEY -> R.array.hifi_mode_entry_values
        Constants.SOUND_PRESET_KEY -> R.array.sound_preset_entry_values
        Constants.DIGITAL_FILTER_KEY -> R.array.digital_filter_entry_values
        Constants.CUSTOM_FILTER_SHAPE_KEY -> R.array.custom_filter_shapes
        Constants.CUSTOM_FILTER_SYMMETRY_KEY -> R.array.custom_filter_symmetries
        else -> 0
    }
    return if (arrayRes != 0) context.resources.getStringArray(arrayRes).toList() else emptyList()
}

/** Maps a Settings-search preference key to a LazyColumn item key (scroll target). */
object SearchKeyMapper {
    fun sectionFor(key: String?): String? = when (key) {
        Constants.DAC_SWITCH_KEY -> "dac_switch"
        Constants.HIFI_MODE_KEY -> Constants.HIFI_MODE_KEY
        Constants.AVC_VOLUME_KEY -> Constants.AVC_VOLUME_KEY
        Constants.SOUND_PRESET_KEY -> Constants.SOUND_PRESET_KEY
        Constants.DIGITAL_FILTER_KEY -> Constants.DIGITAL_FILTER_KEY
        Constants.CUSTOM_FILTER_SHAPE_KEY -> Constants.CUSTOM_FILTER_SHAPE_KEY
        Constants.CUSTOM_FILTER_SYMMETRY_KEY -> Constants.CUSTOM_FILTER_SYMMETRY_KEY
        Constants.RESET_COEFFICIENTS_KEY -> Constants.RESET_COEFFICIENTS_KEY
        Constants.BALANCE_KEY -> Constants.BALANCE_KEY
        else -> null
    }
}

private const val TAG = "QuadDACPanel"
private const val COEFF_RANGE_FIRST = -9999999
private const val COEFF_RANGE_LAST = 9999999
