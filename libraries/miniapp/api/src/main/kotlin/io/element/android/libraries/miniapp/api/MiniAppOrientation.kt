/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.api

/**
 * Screen orientation a mini-app runs in: the backend's `screen_orientation` app setting, then
 * whatever the page asks for through the `orientation.set` bridge call.
 *
 * @param wireName The name used by the JS bridge (`orientation.get` / `orientation.set`).
 */
enum class MiniAppOrientation(val wireName: String) {
    /** Portrait only (the default). */
    PORTRAIT("portrait"),

    /** Both landscape directions, following the sensor. */
    LANDSCAPE("landscape"),

    /** Follow the device. */
    AUTO("auto");

    companion object {
        /**
         * Parse the backend's `screen_orientation`: 1 = portrait, 2 = landscape, 3 = auto.
         * Missing or unknown values are portrait.
         */
        fun fromServerValue(value: Int?): MiniAppOrientation = when (value) {
            2 -> LANDSCAPE
            3 -> AUTO
            else -> PORTRAIT
        }

        /** Parse a bridge name ("portrait" | "landscape" | "auto"), or null when invalid. */
        fun fromWireName(name: String?): MiniAppOrientation? = entries.firstOrNull { it.wireName == name }
    }
}
