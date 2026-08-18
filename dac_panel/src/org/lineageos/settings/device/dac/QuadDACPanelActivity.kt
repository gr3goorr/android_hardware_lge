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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Hosts the Compose QuadDAC panel. Keeps the EXTRA_SETTINGS / QS_TILE_PREFERENCES
 * intent filters and the com.android.settings.* meta-data from the manifest, so it
 * stays reachable from Settings, QS tile preferences and Settings search
 * (via the QuadDACPanelFragment activity-alias).
 */
class QuadDACPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val searchKey = intent.getStringExtra(EXTRA_FRAGMENT_ARG_KEY)
        setContent {
            QuadDACPanelTheme {
                QuadDACPanelScreen(
                    initialSearchKey = searchKey,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        /** Contract value used by SettingsIntelligence for search result deep links. */
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    }
}
