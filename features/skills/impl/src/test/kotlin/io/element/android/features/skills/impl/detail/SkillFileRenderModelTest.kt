/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillFileRenderModelTest {
    @Test
    fun `buildSkillFileRenderModels - mirrors iOS query path extraction and preupload alignment`() {
        val items = buildSkillFileRenderModels(
            skillId = "weather",
            presignedUrls = listOf(
                "https://files.example/download?filepath=production/skills/weather/SKILL.md&signature=1",
                "https://files.example/download?object_key=production%2Fskills%2Fweather%2Fscripts%2Frun.py",
            ),
            preuploadUrls = listOf("https://upload.example/skill.md"),
        )

        assertThat(items.map { it.displayPath }).containsExactly("SKILL.md", "scripts/run.py").inOrder()
        assertThat(items[0].preuploadUrl).isEqualTo("https://upload.example/skill.md")
        assertThat(items[0].isEditable).isTrue()
        assertThat(items[1].preuploadUrl).isNull()
        assertThat(items[0].icon).isEqualTo(SkillFileIcon.Text)
        assertThat(items[1].icon).isEqualTo(SkillFileIcon.Document)
    }

    @Test
    fun `buildSkillFileRenderModels - trims common directory prefix after skill scoping`() {
        val items = buildSkillFileRenderModels(
            skillId = "skill-123",
            presignedUrls = listOf(
                "https://bucket.example/app/skills/skill-123/src/main.py",
                "https://bucket.example/app/skills/skill-123/src/lib/helper.py",
            ),
        )

        assertThat(items.map { it.displayPath }).containsExactly("main.py", "lib/helper.py").inOrder()
    }

    @Test
    fun `buildSkillFileRenderModels - falls back to file name for long object keys`() {
        val items = buildSkillFileRenderModels(
            skillId = "other-skill",
            presignedUrls = listOf("https://bucket.example/prod/archive/uploads/random/package/config.json"),
        )

        assertThat(items.single().displayPath).isEqualTo("config.json")
        assertThat(items.single().icon).isEqualTo(SkillFileIcon.Code)
    }
}
