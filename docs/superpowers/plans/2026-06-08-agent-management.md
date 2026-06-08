# Agent Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native Android Agent Management feature matching the iOS list/detail/basic create/edit flows without Rust SDK, voice, vault, sandbox runtime, or Unseal component-library dependencies.

**Architecture:** Add a standalone `features/agentmanagement` feature with `api`, `impl`, and `test` modules. The implementation follows existing Element X Android Appyx Node + `Presenter<State>` patterns and uses `ChatbotApiService` from the completed `chatbot-api-service` spec.

**Tech Stack:** Kotlin, Compose, Appyx, Metro DI, coroutines, immutable collections, existing Element X design system, `libraries/chatbot/api`, `libraries/chatbot/test`, Matrix client abstractions for direct chat seams.

---

## File Structure

Create:

- `features/agentmanagement/api/build.gradle.kts`: public feature module.
- `features/agentmanagement/api/src/main/kotlin/io/element/android/features/agentmanagement/api/AgentManagementEntryPoint.kt`: entry point, initial target, callbacks.
- `features/agentmanagement/impl/build.gradle.kts`: implementation module.
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/DefaultAgentManagementEntryPoint.kt`: AppScope entry point binding.
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/AgentManagementFlowNode.kt`: backstack flow for list/detail/create/edit.
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list/*`: list state/events/presenter/node/view.
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/*`: detail state/events/presenter/node/view.
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/*`: create/edit state/events/presenter/node/view.
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/*`: shared agent formatting, direct chat service seam, provider form helpers, loading/error mapper.
- `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/*`: presenter tests.
- `features/agentmanagement/test/build.gradle.kts`: fake/test helper module.
- `features/agentmanagement/test/src/main/kotlin/io/element/android/features/agentmanagement/test/FakeAgentManagementEntryPoint.kt`: test entry point.

Modify:

- `settings.gradle.kts` only if the repository does not auto-include `features/agentmanagement/*`.
- No settings/start-chat/preference integration in this plan unless required for compilation. App navigation integration belongs to a small follow-up once the standalone feature is green.

Do not create:

- Any dependency on `libraries/rustsdk`, `voiceplayer`, `voicerecorder`, vault modules, `UnsealUI`, `UnsealAgent`, or `UnsealMiniApp`.
- Any implementation of skills marketplace/skill picker, vault management, sandbox setup, or voice config.

---

### Task 1: Feature Module And Entry Point

**Files:**
- Create: `features/agentmanagement/api/build.gradle.kts`
- Create: `features/agentmanagement/api/src/main/kotlin/io/element/android/features/agentmanagement/api/AgentManagementEntryPoint.kt`
- Create: `features/agentmanagement/impl/build.gradle.kts`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/DefaultAgentManagementEntryPoint.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/AgentManagementFlowNode.kt`
- Create: `features/agentmanagement/test/build.gradle.kts`
- Create: `features/agentmanagement/test/src/main/kotlin/io/element/android/features/agentmanagement/test/FakeAgentManagementEntryPoint.kt`

- [ ] **Step 1: Write API module build file**

Create `features/agentmanagement/api/build.gradle.kts`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.agentmanagement.api"
}

dependencies {
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.matrix.api)
}
```

- [ ] **Step 2: Write API entry point**

Create `features/agentmanagement/api/src/main/kotlin/io/element/android/features/agentmanagement/api/AgentManagementEntryPoint.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import kotlinx.parcelize.Parcelize

interface AgentManagementEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object List : InitialTarget

        @Parcelize
        data class Detail(val botName: String) : InitialTarget

        @Parcelize
        data object Create : InitialTarget

        @Parcelize
        data class Edit(val botName: String) : InitialTarget
    }

    data class Params(val initialTarget: InitialTarget = InitialTarget.List) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias)
        fun onOpenSkills(botName: String?)
        fun onOpenCreatedDirectRoom(roomId: RoomId)
    }
}
```

- [ ] **Step 3: Write implementation module build file**

Create `features/agentmanagement/impl/build.gradle.kts`:

```kotlin
import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.agentmanagement.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

dependencies {
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.chatbot.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.matrixui)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.analytics.api)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    api(projects.features.agentmanagement.api)

    testCommonDependencies(libs, true)
    testImplementation(projects.features.agentmanagement.test)
    testImplementation(projects.libraries.chatbot.test)
    testImplementation(projects.libraries.matrix.test)
}
```

- [ ] **Step 4: Write test module build file**

Create `features/agentmanagement/test/build.gradle.kts`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.features.agentmanagement.test"
}

dependencies {
    api(projects.features.agentmanagement.api)
    implementation(projects.libraries.architecture)
    implementation(projects.tests.testutils)
}
```

- [ ] **Step 5: Write default entry point binding**

Create `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/DefaultAgentManagementEntryPoint.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.agentmanagement.api.AgentManagementEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultAgentManagementEntryPoint : AgentManagementEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: AgentManagementEntryPoint.Params,
        callback: AgentManagementEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<AgentManagementFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback),
        )
    }
}
```

- [ ] **Step 6: Write flow node skeleton**

Create `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/AgentManagementFlowNode.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.agentmanagement.api.AgentManagementEntryPoint
import io.element.android.features.agentmanagement.impl.detail.AgentDetailNode
import io.element.android.features.agentmanagement.impl.edit.AgentEditNode
import io.element.android.features.agentmanagement.impl.list.AgentListNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class AgentManagementFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<AgentManagementFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<AgentManagementEntryPoint.Params>().first().initialTarget.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object List : NavTarget

        @Parcelize
        data class Detail(val botName: String) : NavTarget

        @Parcelize
        data object Create : NavTarget

        @Parcelize
        data class Edit(val botName: String) : NavTarget
    }

    private val callback: AgentManagementEntryPoint.Callback = callback()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.List -> createNode<AgentListNode>(
                buildContext = buildContext,
                plugins = listOf(
                    object : AgentListNode.Callback {
                        override fun onDone() = closeFlow()
                        override fun onCreateAgent() = backstack.push(NavTarget.Create)
                        override fun onOpenAgent(botName: String) = backstack.push(NavTarget.Detail(botName))
                        override fun onOpenSkills() = callback.onOpenSkills(null)
                    }
                ),
            )
            is NavTarget.Detail -> createNode<AgentDetailNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentDetailNode.Inputs(navTarget.botName),
                    object : AgentDetailNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onEdit(botName: String) = backstack.push(NavTarget.Edit(botName))
                        override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) = callback.onOpenRoom(roomIdOrAlias)
                        override fun onOpenSkills(botName: String) = callback.onOpenSkills(botName)
                    }
                ),
            )
            NavTarget.Create -> createNode<AgentEditNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentEditNode.Inputs.Create,
                    object : AgentEditNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onCreated(botName: String, directRoomId: RoomId?) {
                            if (directRoomId != null) {
                                callback.onOpenCreatedDirectRoom(directRoomId)
                            } else {
                                backstack.push(NavTarget.Detail(botName))
                            }
                        }
                        override fun onUpdated(botName: String) = backstack.push(NavTarget.Detail(botName))
                    }
                ),
            )
            is NavTarget.Edit -> createNode<AgentEditNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentEditNode.Inputs.Edit(navTarget.botName),
                    object : AgentEditNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onCreated(botName: String, directRoomId: RoomId?) = backstack.push(NavTarget.Detail(botName))
                        override fun onUpdated(botName: String) {
                            backstack.pop()
                        }
                    }
                ),
            )
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier)
    }

    private fun closeOrPop() {
        if (backstack.canPop()) {
            backstack.pop()
        } else {
            callback.onDone()
        }
    }

    private fun closeFlow() {
        callback.onDone()
    }
}

private fun AgentManagementEntryPoint.InitialTarget.toNavTarget(): AgentManagementFlowNode.NavTarget = when (this) {
    AgentManagementEntryPoint.InitialTarget.List -> AgentManagementFlowNode.NavTarget.List
    is AgentManagementEntryPoint.InitialTarget.Detail -> AgentManagementFlowNode.NavTarget.Detail(botName)
    AgentManagementEntryPoint.InitialTarget.Create -> AgentManagementFlowNode.NavTarget.Create
    is AgentManagementEntryPoint.InitialTarget.Edit -> AgentManagementFlowNode.NavTarget.Edit(botName)
}
```

- [ ] **Step 7: Write fake test entry point**

Create `features/agentmanagement/test/src/main/kotlin/io/element/android/features/agentmanagement/test/FakeAgentManagementEntryPoint.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.agentmanagement.api.AgentManagementEntryPoint

class FakeAgentManagementEntryPoint : AgentManagementEntryPoint {
    val createdNodes = mutableListOf<AgentManagementEntryPoint.Params>()

    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: AgentManagementEntryPoint.Params,
        callback: AgentManagementEntryPoint.Callback,
    ): Node {
        createdNodes += params
        return object : Node(buildContext) {
            @Composable
            override fun View(modifier: Modifier) = Unit
        }
    }
}
```

- [ ] **Step 8: Run module skeleton compile**

Run:

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:api:compileDebugKotlin :features:agentmanagement:test:compileDebugKotlin
```

Expected: build succeeds. If Gradle says the project path is unknown, add the three new modules to the repository module include mechanism or `settings.gradle.kts`, then rerun the same command.

- [ ] **Step 9: Commit**

```bash
git add features/agentmanagement docs/superpowers/specs/2026-06-08-agent-management-design.md docs/superpowers/plans/2026-06-08-agent-management.md
git commit -m "feat: add agent management feature shell"
```

---

### Task 2: Shared Agent Management Helpers And Direct Chat Seam

**Files:**
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentFormatter.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentDirectChatService.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentProviderForm.kt`
- Test: `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentFormatterTest.kt`
- Test: `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentProviderFormTest.kt`

- [ ] **Step 1: Write formatter tests**

Create `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentFormatterTest.kt`:

```kotlin
package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentFormatterTest {
    @Test
    fun `matrix id uses localpart and server name`() {
        val agent = ChatbotAgent(botName = "helper", localpart = "agent-helper", serverName = "unseal.test")
        assertEquals("@agent-helper:unseal.test", agent.matrixId())
    }

    @Test
    fun `direct chat id falls back to valid provider mxid`() {
        val agent = ChatbotAgent(botName = "helper", providerAgentId = "@bot:unseal.test")
        assertEquals("@bot:unseal.test", agent.agentMatrixUserId())
    }

    @Test
    fun `invalid provider id is ignored for direct chat`() {
        val agent = ChatbotAgent(botName = "helper", providerAgentId = "bot")
        assertNull(agent.agentMatrixUserId())
    }

    @Test
    fun `provider model text joins non blank parts`() {
        val agent = ChatbotAgent(botName = "helper", provider = "openai", model = "gpt-4.1")
        assertEquals("openai · gpt-4.1", agent.providerModelText())
    }

    @Test
    fun `copy id fallback matches ios`() {
        assertEquals("plain", ChatbotAgent(botName = "plain").copyableAgentId())
        assertEquals("@local:server", ChatbotAgent(botName = "plain", localpart = "local", serverName = "server").copyableAgentId())
        assertEquals("@pid:server", ChatbotAgent(botName = "plain", providerAgentId = "@pid:server").copyableAgentId())
    }
}
```

- [ ] **Step 2: Implement formatter**

Create `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentFormatter.kt`:

```kotlin
package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent

fun ChatbotAgent.displayTitle(): String = displayName?.takeIf { it.isNotBlank() } ?: botName

fun ChatbotAgent.matrixId(): String? {
    val local = localpart?.takeIf { it.isNotBlank() } ?: return null
    val server = serverName?.takeIf { it.isNotBlank() } ?: return null
    return "@$local:$server"
}

fun ChatbotAgent.providerModelText(): String? {
    return listOfNotNull(provider?.trim(), model?.trim())
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " · ")
}

fun ChatbotAgent.agentMatrixUserId(): String? {
    matrixId()?.let { return it }
    val providerId = providerAgentId?.trim().orEmpty()
    return providerId.takeIf { it.startsWith("@") && it.contains(":") }
}

fun ChatbotAgent.copyableAgentId(): String = agentMatrixUserId() ?: matrixId() ?: botName
```

- [ ] **Step 3: Write provider form tests**

Create `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentProviderFormTest.kt`:

```kotlin
package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProviderInfo
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentProviderFormTest {
    @Test
    fun `unseal provider is ordered first`() {
        val providers = listOf(provider("openai"), provider("unseal"), provider("anthropic"))
        assertEquals(listOf("unseal", "openai", "anthropic"), providers.iosOrderedProviderIds())
    }

    @Test
    fun `first provider is selected when none selected`() {
        val providers = listOf(provider("unseal"), provider("openai"))
        assertEquals("unseal", selectProviderId(providers, null))
    }

    @Test
    fun `first model is selected when current model is absent`() {
        val provider = provider("openai", models = listOf("gpt-4.1", "gpt-4.1-mini"))
        assertEquals("gpt-4.1", selectModelId(provider, "missing"))
    }

    @Test
    fun `api key is not needed for unseal provider`() {
        assertFalse(needsApiKey("unseal"))
        assertTrue(needsApiKey("openai"))
    }

    private fun provider(id: String, models: List<String> = emptyList()) = ChatbotAgentProvider(
        id = id,
        displayName = id,
        info = ChatbotAgentProviderInfo(models = models.map { ChatbotProviderModel(id = it) }),
    )
}
```

- [ ] **Step 4: Implement provider helpers**

Create `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentProviderForm.kt`:

```kotlin
package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider

fun List<ChatbotAgentProvider>.iosOrderedProviders(): List<ChatbotAgentProvider> {
    val mutable = toMutableList()
    val unsealIndex = mutable.indexOfFirst { it.id == "unseal" }
    if (unsealIndex > 0) {
        val unseal = mutable.removeAt(unsealIndex)
        mutable.add(0, unseal)
    }
    return mutable
}

fun List<ChatbotAgentProvider>.iosOrderedProviderIds(): List<String> = iosOrderedProviders().map { it.id }

fun selectProviderId(providers: List<ChatbotAgentProvider>, currentProviderId: String?): String? {
    if (currentProviderId != null && providers.any { it.id == currentProviderId }) return currentProviderId
    return providers.firstOrNull()?.id
}

fun selectModelId(provider: ChatbotAgentProvider?, currentModelId: String): String {
    val models = provider?.info?.models.orEmpty()
    if (models.isEmpty()) return currentModelId
    if (currentModelId.isNotEmpty() && models.any { it.id == currentModelId }) return currentModelId
    return models.first().id
}

fun needsApiKey(providerId: String?): Boolean = providerId != null && providerId != "unseal"
```

- [ ] **Step 5: Implement direct chat seam**

Create `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared/AgentDirectChatService.kt`:

```kotlin
package io.element.android.features.agentmanagement.impl.shared

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

interface AgentDirectChatService {
    suspend fun findExistingDirectRoom(userId: String): Result<RoomId?>
    suspend fun createDirectRoom(userId: String, expectedRoomName: String): Result<RoomId>
}

@ContributesBinding(SessionScope::class)
@Inject
class DefaultAgentDirectChatService(
    private val matrixClient: MatrixClient,
) : AgentDirectChatService {
    override suspend fun findExistingDirectRoom(userId: String): Result<RoomId?> {
        return matrixClient.getDmRoom(UserId(userId))
    }

    override suspend fun createDirectRoom(userId: String, expectedRoomName: String): Result<RoomId> {
        return matrixClient.createDirectRoom(UserId(userId), expectedRoomName)
    }
}
```

If `MatrixClient` uses different method names, adapt this file only to the actual Matrix API. Keep the interface unchanged for presenter tests.

- [ ] **Step 6: Run shared tests**

Run:

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:testDebugUnitTest --tests '*AgentFormatterTest' --tests '*AgentProviderFormTest'
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/shared features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/shared
git commit -m "feat: add agent management shared helpers"
```

---

### Task 3: Agent List Flow

**Files:**
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list/AgentListEvents.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list/AgentListState.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list/AgentListPresenter.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list/AgentListNode.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list/AgentListView.kt`
- Test: `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/list/AgentListPresenterTest.kt`

- [ ] **Step 1: Write presenter tests**

Create `AgentListPresenterTest.kt` with tests for load-once, refresh, sorting, search, and failure. Use `FakeChatbotApiService` from `libraries/chatbot/test` and assert state through Compose presenter test helpers used elsewhere in the repo.

Minimum test cases:

```kotlin
@Test fun `present - loads agents once and sorts by display title`()
@Test fun `event - refresh reloads agents`()
@Test fun `event - search filters bot name display name and description case insensitively`()
@Test fun `present - load failure keeps previous data and exposes error`()
```

- [ ] **Step 2: Implement state/events**

State must include:

```kotlin
data class AgentListState(
    val agents: ImmutableList<ChatbotAgent>,
    val filteredAgents: ImmutableList<ChatbotAgent>,
    val searchQuery: String,
    val isLoading: Boolean,
    val error: String?,
    val eventSink: (AgentListEvents) -> Unit,
)
```

Events must include:

```kotlin
sealed interface AgentListEvents {
    data object OnAppear : AgentListEvents
    data object Refresh : AgentListEvents
    data class SearchQueryChanged(val query: String) : AgentListEvents
    data object CreateAgent : AgentListEvents
    data class SelectAgent(val botName: String) : AgentListEvents
    data object OpenSkills : AgentListEvents
    data object ClearError : AgentListEvents
}
```

- [ ] **Step 3: Implement presenter**

Presenter behavior:

- keep mutable Compose states for agents, search query, loading, error, and hasLoadedOnce;
- on appear, load only when `hasLoadedOnce` is false;
- `Refresh` always reloads;
- sort by `agent.displayName ?: agent.botName`;
- filter by trimmed query over `botName`, `displayName`, `description`, ignoring case;
- call node callback through a navigator interface for create/detail/skills.

- [ ] **Step 4: Implement node and view**

Node:

- `@ContributesNode(SessionScope::class)`
- callback methods: `onDone`, `onCreateAgent`, `onOpenAgent(botName)`, `onOpenSkills`.
- Render `AgentListView`.

View:

- use `PreferencePage` or a simple existing top app bar scaffold;
- show title `Agents`;
- show create action;
- show search field;
- show loading skeleton rows;
- show empty state;
- show list rows with display title, provider/model, description, public/private badge.

- [ ] **Step 5: Run tests**

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:testDebugUnitTest --tests '*AgentListPresenterTest'
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/list features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/list
git commit -m "feat: add agent list flow"
```

---

### Task 4: Agent Detail Flow

**Files:**
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailEvents.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailState.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailPresenter.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailNode.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailView.kt`
- Test: `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailPresenterTest.kt`

- [ ] **Step 1: Write presenter tests**

Minimum test cases:

```kotlin
@Test fun `present - derives title matrix id provider model and direct chat id`()
@Test fun `present - loads agent and rooms on appear`()
@Test fun `present - preserves partial agent when fresh load fails`()
@Test fun `event - copy uses direct chat id then matrix id then bot name`()
@Test fun `event - start chat opens existing direct room`()
@Test fun `event - start chat creates direct room when none exists and joins agent room best effort`()
@Test fun `event - leave room calls api and reloads rooms`()
```

- [ ] **Step 2: Implement state/events**

State must include:

```kotlin
data class AgentDetailState(
    val botName: String,
    val agent: ChatbotAgent?,
    val rooms: ImmutableList<ChatbotAgentRoom>,
    val isLoading: Boolean,
    val isSoulExpanded: Boolean,
    val error: String?,
    val navigationTitle: String,
    val matrixId: String?,
    val providerModelText: String?,
    val agentMatrixUserId: String?,
    val eventSink: (AgentDetailEvents) -> Unit,
)
```

Events must include `OnAppear`, `Refresh`, `Edit`, `CopyAgentId`, `ToggleSoulExpanded`, `ManageSkills`, `StartChat`, `OpenRoom(roomId)`, `LeaveRoom(roomId)`, and `ClearError`.

- [ ] **Step 3: Implement presenter**

Presenter behavior:

- load `getAgent(botName)` and `listAgentRooms(botName)`;
- preserve passed/previous agent when load fails;
- show load error only when no agent is available;
- use `ClipboardHelper.copyPlainText` and snackbar for copy;
- use `AgentDirectChatService` for existing/create direct room;
- after creating direct room, call `ChatbotApiService.agentJoinRoom(botName, roomId.value)` best effort;
- call navigator for edit, skills, and open room.

- [ ] **Step 4: Implement node and view**

View sections:

- profile header with title, Matrix ID, provider/model, public/private badge;
- Start chat and Edit buttons;
- description;
- soul block with expand/collapse if long;
- skills seam row/button that calls `ManageSkills`;
- rooms list with empty state and rows.

- [ ] **Step 5: Run tests**

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:testDebugUnitTest --tests '*AgentDetailPresenterTest'
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/detail
git commit -m "feat: add agent detail flow"
```

---

### Task 5: Agent Create/Edit Flow

**Files:**
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditEvents.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditState.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditPresenter.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditNode.kt`
- Create: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditView.kt`
- Test: `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditPresenterTest.kt`

- [ ] **Step 1: Write presenter tests**

Minimum test cases:

```kotlin
@Test fun `create mode defaults to private and auto join enabled`()
@Test fun `present - providers load with unseal first and first model selected`()
@Test fun `event - bot name availability is available on 404 and taken on success`()
@Test fun `event - submit create trims fields and sends null for empty optional text`()
@Test fun `event - create conflict handles backend already exists 500 as taken`()
@Test fun `event - successful create enters success phase with summary`()
@Test fun `edit mode preloads agent fields and auto join fallback`()
@Test fun `event - submit edit maps update request and emits updated action`()
```

- [ ] **Step 2: Implement state/events**

State must represent:

- mode `Create` or `Edit(botName)`;
- providers;
- `AgentNameAvailability.Unknown/Checking/Available/Taken`;
- `AgentEditPhase.Editing/Submitting(step)/Success(summary)`;
- bindable fields for bot name, display name, description, avatar URL, public flag, auto-join flag, provider ID, model, API key, base URL, and soul.

Events must include `OnAppear`, `RefreshProviders`, field change events, `BotNameChanged`, `ProviderChanged`, `Submit`, `GoToChat`, `CreateAnother`, and `ClearError`.

- [ ] **Step 3: Implement presenter**

Presenter behavior:

- Create defaults: `isPublic=false`, `autoJoin=true`.
- On appear: load providers; in edit mode also load agent.
- Provider list: move `unseal` to first.
- If provider not selected, select first provider.
- If selected provider has models and current model is empty/missing, select first model.
- Name availability: debounce 450 ms, `getAgent` success means taken, HTTP 404 means available, other errors unknown.
- Submit create:
  - trim fields;
  - require non-empty bot name;
  - preflight name conflict;
  - send `ChatbotCreateAgentRequest` with empty optional text as null and `ChatbotAgentSettings(autoJoin = autoJoin)`;
  - handle HTTP 500 body containing `already` or `exists` as name conflict;
  - attempt direct room creation with `AgentDirectChatService`;
  - best-effort `agentJoinRoom`;
  - set success summary.
- Submit edit:
  - trim fields;
  - send `ChatbotUpdateAgentRequest` with empty optional text as null and `ChatbotAgentSettings(autoJoin = autoJoin)`;
  - emit updated callback.
- Do not call voice, vault, sandbox, or skills APIs.

- [ ] **Step 4: Implement node and view**

View:

- form title `Create Agent` or `Edit Agent`;
- identity section;
- visibility section;
- provider/model/API settings section;
- soul section;
- submit section;
- success phase with created agent summary and actions for go to chat/create another/done.

- [ ] **Step 5: Run tests**

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:testDebugUnitTest --tests '*AgentEditPresenterTest'
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/edit
git commit -m "feat: add agent create edit flow"
```

---

### Task 6: Verification, Dependency Audit, And Self-Review

**Files:**
- Modify tests only if verification exposes gaps.

- [ ] **Step 1: Run feature tests**

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:testDebugUnitTest
```

Expected: all agent management tests pass.

- [ ] **Step 2: Run compile checks**

```bash
./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:compileDebugKotlin :features:agentmanagement:impl:compileDebugUnitTestKotlin
```

Expected: compile succeeds.

- [ ] **Step 3: Re-run chatbot foundation tests**

```bash
./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:api:testDebugUnitTest :libraries:chatbot:impl:testDebugUnitTest :libraries:chatbot:test:testDebugUnitTest
```

Expected: tests pass or no-source where appropriate.

- [ ] **Step 4: Run forbidden dependency scan**

```bash
rg -n "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|UnsealUI|UnsealAgent|UnsealMiniApp|Vault|vault|sandbox|Sandbox" features/agentmanagement -g '!**/build/**'
```

Expected: no output except allowed text in spec/plan. If code output appears, remove the dependency or move that work to its later feature spec.

- [ ] **Step 5: Run unfinished-work scan**

```bash
rg -n "TODO|FIXME|TBD|implement later|fill in details|Not implemented|error\\(" features/agentmanagement -g '!**/build/**'
```

Expected: no output in production source. Test sentinels are acceptable only when they fail on unexpected calls with a clear message.

- [ ] **Step 6: Inspect final status**

```bash
git status --short --branch
```

Expected: clean working tree after commits, or only intentional uncommitted review fixes.

- [ ] **Step 7: Commit final review fixes if any**

```bash
git add features/agentmanagement docs/superpowers/specs/2026-06-08-agent-management-design.md docs/superpowers/plans/2026-06-08-agent-management.md
git commit -m "test: verify agent management migration"
```

Only run this commit command if Step 1-5 required changes after the Task 1-5 commits.

---

## Self-Review

Spec coverage:

- List, detail, create, and edit are covered by Tasks 3-5.
- Entry point and navigation seams are covered by Task 1.
- Shared iOS behavior helpers are covered by Task 2.
- Dependency separation is covered by Task 6.
- Skills, vault, sandbox, voice, miniapp, rich AI rendering, and Rust SDK work are explicitly excluded and scanned.

Unfinished-work scan:

- The plan avoids unfinished-work markers outside the explicit source scan command.
- Code-producing steps provide exact target files, state/events, behavioral requirements, and verification tests.
- Where a presenter or view file depends on exact project APIs discovered during execution, the task still locks the state shape, event names, iOS behavior, and tests that prove correctness.

Type consistency:

- Entry point initial targets map to flow node nav targets.
- List/detail/edit state and event names are stable across tasks.
- Shared helper names used by later tasks are defined in Task 2.
