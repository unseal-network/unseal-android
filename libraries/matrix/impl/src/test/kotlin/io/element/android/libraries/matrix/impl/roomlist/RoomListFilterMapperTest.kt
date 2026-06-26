/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import org.junit.Test
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind.All
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind.Any
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind.Invite
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind.NonSpace
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind.Space

class RoomListFilterMapperTest {
    @Test
    fun `default filter should include room invites and space invites`() {
        val result = RoomListFilterMapper.toRustFilter(RoomListFilter.All(emptyList()))

        assertThat(result).isInstanceOf(All::class.java)
        val filters = (result as All).filters
        val baseFilter = filters.filterIsInstance<Any>().first()

        assertThat(baseFilter.filters).contains(All(listOf(NonSpace, Invite)))
        assertThat(baseFilter.filters).contains(All(listOf(Space, Invite)))
    }
}
