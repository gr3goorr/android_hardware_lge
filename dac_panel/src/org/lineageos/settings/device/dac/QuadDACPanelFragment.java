/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.device.dac;

/**
 * Intentional empty shell. Kept only as the stable component identity for the
 * Settings search integration:
 *   - QuadDACPanelSearchIndexablesProvider reports this class name as the search
 *     result target;
 *   - AndroidManifest.xml declares an activity-alias with this name that resolves
 *     search result clicks to QuadDACPanelActivity.
 * Settings never instantiates this class, so it has no UI or logic of its own.
 */
public final class QuadDACPanelFragment {
    private QuadDACPanelFragment() {
    }
}
