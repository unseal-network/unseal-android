/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.audio.api

enum class AudioFocusRequester {
    ElementCall,
    VoiceMessage,
    RecordVoiceMessage,
    MediaViewer,
}

enum class AudioFocusLoss {
    Transient,
    Permanent,
}

interface AudioFocus {
    /**
     * Request audio focus for the given requester.
     * @param requester The mode for which to request audio focus.
     * @param onFocusGained Callback to be invoked when audio focus is regained.
     * @param onFocusLost Callback to be invoked with the kind of audio focus loss.
     * @return true if the audio focus was successfully requested, false otherwise.
     */
    fun requestAudioFocus(
        requester: AudioFocusRequester,
        onFocusGained: () -> Unit = {},
        onFocusLost: (AudioFocusLoss) -> Unit,
    )

    /**
     * Release the audio focus.
     */
    fun releaseAudioFocus()
}
