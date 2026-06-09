# Credits Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android Credits & Billing dashboard from the iOS Credits implementation, excluding Stripe top-up checkout.

**Architecture:** Add a new `features/credits` API/implementation/test feature family following the existing `features/webhooks` entry-point and Appyx node pattern. Keep API loading in presenters through `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)`, keep formatting logic pure and unit-tested, then wire Settings to the new `CreditsEntryPoint` with top-up as a callback seam only.

**Tech Stack:** Kotlin, Jetpack Compose, Appyx, Metro dependency injection, `ChatbotApiService`, Matrix session abstractions, existing Element Android design system, Turbine presenter tests, Gradle project accessors.

---

## Source Of Truth

- Spec: `docs/superpowers/specs/2026-06-09-credits-dashboard-design.md`
- iOS references:
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/CreditsScreenModels.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/CreditsScreenViewModel.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/View/CreditsScreen.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/View/SettingsScreen.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/SettingsScreenViewModel.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`

## File Structure

- Create `features/credits/api/build.gradle.kts`: public entry point module.
- Create `features/credits/api/src/main/kotlin/io/element/android/features/credits/api/CreditsEntryPoint.kt`: `FeatureEntryPoint`, tab enum, params, callbacks.
- Create `features/credits/impl/build.gradle.kts`: Compose implementation module with Metro and tests.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/DefaultCreditsEntryPoint.kt`: App-scope binding.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsFlowNode.kt`: single-screen flow node.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsNode.kt`: screen node, callback bridge.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsEvents.kt`: presenter events.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsState.kt`: immutable screen state.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsPresenter.kt`: iOS-equivalent loading and mutation logic.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsView.kt`: Compose UI.
- Create `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/model/CreditFormatters.kt`: micros, dollars, low-balance, period, and local-day range helpers.
- Create `features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl/model/CreditFormattersTest.kt`: formatter tests.
- Create `features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl/CreditsPresenterTest.kt`: presenter tests.
- Create `features/credits/test/build.gradle.kts`: test fake module.
- Create `features/credits/test/src/main/kotlin/io/element/android/features/credits/test/FakeCreditsEntryPoint.kt`: fake entry point for Preferences tests.
- Modify `features/preferences/impl/build.gradle.kts`: depend on credits API/test and chatbot API/test.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`: inject `CreditsEntryPoint` and add tab-specific nav targets.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootState.kt`: add credit balance load state.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenter.kt`: load balance through `ChatbotApiService`.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`: add Settings credit card.
- Modify `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`: fake credits entry point and nav assertions.
- Modify `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenterTest.kt`: credit loading success/failure tests.
- Modify `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`: loading/loaded/unavailable card rendering and click tests.

---

### Task 1: Feature Module Shell And Entry Point

**Files:**
- Create: `features/credits/api/build.gradle.kts`
- Create: `features/credits/api/src/main/kotlin/io/element/android/features/credits/api/CreditsEntryPoint.kt`
- Create: `features/credits/impl/build.gradle.kts`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/DefaultCreditsEntryPoint.kt`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsFlowNode.kt`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsNode.kt`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsEvents.kt`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsState.kt`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsPresenter.kt`
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsView.kt`
- Create: `features/credits/test/build.gradle.kts`
- Create: `features/credits/test/src/main/kotlin/io/element/android/features/credits/test/FakeCreditsEntryPoint.kt`

- [ ] **Step 1: Create API module**

`features/credits/api/build.gradle.kts`:

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
    namespace = "io.element.android.features.credits.api"
}

dependencies {
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.chatbot.api)
}
```

`features/credits/api/src/main/kotlin/io/element/android/features/credits/api/CreditsEntryPoint.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance

interface CreditsEntryPoint : FeatureEntryPoint {
    enum class CreditsTab {
        Balance,
        DailyUsage,
        Usage,
    }

    data class Params(
        val initialTab: CreditsTab = CreditsTab.Balance,
    ) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onTopUpRequested(balance: CreditBalance?)
    }
}
```

- [ ] **Step 2: Create implementation module and default entry point**

`features/credits/impl/build.gradle.kts`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.credits.impl"

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
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    api(projects.features.credits.api)

    testCommonDependencies(libs, true)
    testImplementation(projects.features.credits.test)
    testImplementation(projects.libraries.chatbot.test)
    testImplementation(projects.libraries.matrix.test)
}
```

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/DefaultCreditsEntryPoint.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultCreditsEntryPoint : CreditsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: CreditsEntryPoint.Params,
        callback: CreditsEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<CreditsFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback),
        )
    }
}
```

- [ ] **Step 3: Create temporary flow, node, presenter, state, events, and view**

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsFlowNode.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class CreditsFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext, plugins = plugins) {
    private val params = plugins<CreditsEntryPoint.Params>().first()
    private val callback = plugins<CreditsEntryPoint.Callback>().first()
    private val node = createNode<CreditsNode>(
        buildContext = buildContext,
        plugins = listOf(
            CreditsNode.Inputs(params.initialTab),
            object : CreditsNode.Callback {
                override fun onDone() = callback.onDone()
                override fun onTopUpRequested(balance: io.element.android.libraries.chatbot.api.model.credits.CreditBalance?) {
                    callback.onTopUpRequested(balance)
                }
            },
        ),
    )

    @Composable
    override fun View(modifier: Modifier) {
        node.View(modifier)
    }
}
```

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsNode.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class CreditsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: CreditsPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val initialTab: CreditsEntryPoint.CreditsTab) : Plugin

    interface Callback : Plugin {
        fun onDone()
        fun onTopUpRequested(balance: CreditBalance?)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        initialTab = inputs.initialTab,
        navigator = object : CreditsNavigator {
            override fun onDone() = callback.onDone()
            override fun onTopUpRequested(balance: CreditBalance?) = callback.onTopUpRequested(balance)
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        CreditsView(state = presenter.present(), modifier = modifier)
    }
}
```

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsEvents.kt`:

```kotlin
package io.element.android.features.credits.impl

import io.element.android.features.credits.api.CreditsEntryPoint

sealed interface CreditsEvents {
    data object OnAppear : CreditsEvents
    data class SelectTab(val tab: CreditsEntryPoint.CreditsTab) : CreditsEvents
    data object Dismiss : CreditsEvents
    data object RequestTopUp : CreditsEvents
}
```

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsState.kt`:

```kotlin
package io.element.android.features.credits.impl

import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance

data class CreditsState(
    val selectedTab: CreditsEntryPoint.CreditsTab,
    val balance: CreditBalance?,
    val eventSink: (CreditsEvents) -> Unit,
)
```

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsPresenter.kt`:

```kotlin
package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance

interface CreditsNavigator {
    fun onDone()
    fun onTopUpRequested(balance: CreditBalance?)
}

@AssistedInject
class CreditsPresenter(
    @Assisted private val initialTab: CreditsEntryPoint.CreditsTab,
    @Assisted private val navigator: CreditsNavigator,
) : Presenter<CreditsState> {
    @AssistedFactory
    interface Factory {
        fun create(
            initialTab: CreditsEntryPoint.CreditsTab,
            navigator: CreditsNavigator,
        ): CreditsPresenter
    }

    @Composable
    override fun present(): CreditsState {
        var selectedTab by remember { mutableStateOf(initialTab) }
        var balance by remember { mutableStateOf<CreditBalance?>(null) }

        fun handleEvent(event: CreditsEvents) {
            when (event) {
                CreditsEvents.OnAppear -> Unit
                is CreditsEvents.SelectTab -> selectedTab = event.tab
                CreditsEvents.Dismiss -> navigator.onDone()
                CreditsEvents.RequestTopUp -> navigator.onTopUpRequested(balance)
            }
        }

        return CreditsState(
            selectedTab = selectedTab,
            balance = balance,
            eventSink = ::handleEvent,
        )
    }
}
```

`features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsView.kt`:

```kotlin
package io.element.android.features.credits.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.credits.api.CreditsEntryPoint

@Composable
fun CreditsView(
    state: CreditsState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(CreditsEvents.OnAppear)
    }
    Column(modifier = modifier.padding(16.dp)) {
        Text("Credits & Billing", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { state.eventSink(CreditsEvents.Dismiss) }) {
            Text("Done")
        }
        Text("Selected tab: ${state.selectedTab.name}")
        Button(onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Balance)) }) {
            Text("Balance")
        }
        Button(onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.DailyUsage)) }) {
            Text("Daily Usage")
        }
        Button(onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Usage)) }) {
            Text("Usage")
        }
        Button(onClick = { state.eventSink(CreditsEvents.RequestTopUp) }) {
            Text("Recharge")
        }
    }
}
```

- [ ] **Step 4: Create test fake module**

`features/credits/test/build.gradle.kts`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.features.credits.test"
}

dependencies {
    implementation(projects.features.credits.api)
    implementation(projects.libraries.architecture)
    implementation(projects.tests.testutils)
}
```

`features/credits/test/src/main/kotlin/io/element/android/features/credits/test/FakeCreditsEntryPoint.kt`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeCreditsEntryPoint(
    var createNodeResult: (Node, BuildContext, CreditsEntryPoint.Params, CreditsEntryPoint.Callback) -> Node = { _, _, _, _ -> lambdaError() },
) : CreditsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: CreditsEntryPoint.Params,
        callback: CreditsEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, params, callback)
    }
}
```

- [ ] **Step 5: Run compile for new modules**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:api:compileDebugKotlin :features:credits:test:compileDebugKotlin :features:credits:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit module shell**

```bash
git add features/credits
git commit -m "feat: add credits feature shell"
```

---

### Task 2: Credit Formatters And Time Ranges

**Files:**
- Create: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/model/CreditFormatters.kt`
- Create: `features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl/model/CreditFormattersTest.kt`

- [ ] **Step 1: Write failing formatter tests**

Create tests for:

```kotlin
assertThat("0.00".prefixedDollar()).isEqualTo("$0.00")
assertThat("$4.50".prefixedDollar()).isEqualTo("$4.50")
assertThat(formatMicrosUsd("1500000")).isEqualTo("$1.50")
assertThat(formatMicrosDelta("-250000")).isEqualTo("-$0.25")
assertThat(formatMicrosDelta("250000")).isEqualTo("+$0.25")
assertThat(isLowBalance("999999")).isTrue()
assertThat(isLowBalance("1000000")).isFalse()
assertThat(CreditsPeriod.SevenDays.apiValue).isEqualTo("sevendays")
assertThat(CreditsPeriod.ThirtyDays.apiValue).isEqualTo("thirtydays")
assertThat(CreditsPeriod.All.apiValue).isEqualTo("all")
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:testDebugUnitTest --tests '*CreditFormattersTest'
```

Expected: fails because formatter symbols do not exist.

- [ ] **Step 3: Implement formatter helpers**

Implement `CreditFormatters.kt` with pure functions:

```kotlin
fun String.prefixedDollar(): String = if (startsWith("$")) this else "$$this"

fun formatMicrosUsd(micros: String): String {
    val cents = micros.toLongOrNull()?.let { it / 10_000L } ?: 0L
    return "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

fun formatMicrosDelta(micros: String): String {
    val value = micros.toLongOrNull() ?: 0L
    val sign = if (value >= 0) "+" else "-"
    return sign + formatMicrosUsd(kotlin.math.abs(value).toString())
}
```

Also add `isLowBalance`, `DailyUsageRange`, `CreditsPeriod`, and `localDayRangeEpochSeconds(range, clock)`.

- [ ] **Step 4: Run formatter tests**

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit formatter helpers**

```bash
git add features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/model features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl/model
git commit -m "feat: add credits formatters"
```

---

### Task 3: Credits Presenter Loading And Events

**Files:**
- Modify: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsEvents.kt`
- Modify: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsState.kt`
- Modify: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsPresenter.kt`
- Create: `features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl/CreditsPresenterTest.kt`

- [ ] **Step 1: Write presenter tests**

Cover these test names:

- `present - on appear loads balance ledger daily usage and analytics`
- `event - appear only loads once`
- `event - changing daily range reloads daily usage`
- `event - changing analytics period reloads analytics with iOS lowercase values`
- `event - load more appends ledger results`
- `event - load more is ignored without next cursor`
- `event - api failures preserve existing loaded content`
- `event - top up passes current balance to navigator`

- [ ] **Step 2: Run presenter tests and confirm failure**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:testDebugUnitTest --tests '*CreditsPresenterTest'
```

Expected: fails because full presenter state/events are not implemented.

- [ ] **Step 3: Implement presenter state and loading**

Use these state fields:

```kotlin
val selectedTab: CreditsEntryPoint.CreditsTab
val balance: CreditBalance?
val transactions: ImmutableList<CreditLedgerItem>
val transactionsCursor: String?
val dailyUsage: CreditDailyUsageResponse?
val analytics: AnalyticsTokensResponse?
val dailyUsageRange: DailyUsageRange
val usageRankingTab: UsageRankingTab
val analyticsPeriod: CreditsPeriod
val isBalanceLoading: Boolean
val isLedgerLoading: Boolean
val isDailyUsageLoading: Boolean
val isAnalyticsLoading: Boolean
val isLoadingMoreTransactions: Boolean
val error: String?
```

Use these events:

```kotlin
data object OnAppear
data class SelectTab(val tab: CreditsEntryPoint.CreditsTab)
data class SelectDailyUsageRange(val range: DailyUsageRange)
data class SelectUsageRankingTab(val tab: UsageRankingTab)
data class SelectAnalyticsPeriod(val period: CreditsPeriod)
data object LoadMoreTransactions
data object ClearError
data object Dismiss
data object RequestTopUp
```

Presenter rules:

- On first `OnAppear`, create service via `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)`.
- Launch balance, ledger, daily usage, and analytics loads independently.
- Use ledger limit `10` and `cursor = null` for initial load.
- Use `transactionsCursor` for load-more.
- Do not clear existing content when a load fails.
- Store failure message in `error`.
- `RequestTopUp` calls navigator with current `balance`.

- [ ] **Step 4: Run presenter tests**

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit presenter**

```bash
git add features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl
git commit -m "feat: add credits dashboard presenter"
```

---

### Task 4: Credits Compose Screen

**Files:**
- Modify: `features/credits/impl/src/main/kotlin/io/element/android/features/credits/impl/CreditsView.kt`
- Create: `features/credits/impl/src/test/kotlin/io/element/android/features/credits/impl/CreditsViewTest.kt`

- [ ] **Step 1: Write view tests**

Cover:

- title `Credits & Billing`
- tabs `Balance`, `Daily Usage`, `Usage`
- balance tab displays prefixed balance and user id
- transactions empty state and load-more button
- daily usage range buttons `7 days` and `30 days`
- usage ranking controls `Agent`, `Model`, `7 days`, `30 days`, `All`
- recharge click emits `RequestTopUp`

- [ ] **Step 2: Run view tests and confirm failure**

Run `:features:credits:impl:testDebugUnitTest --tests '*CreditsViewTest'`.

- [ ] **Step 3: Implement screen**

Use `PreferencePage` or the local top-app-bar pattern already used in adjacent feature screens, existing design-system `ListItem`, `Text`, `Button`, `HorizontalDivider`, and simple rows instead of adding a chart library.

- [ ] **Step 4: Run view tests and compile**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:testDebugUnitTest :features:credits:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit UI**

```bash
git add features/credits/impl
git commit -m "feat: add credits dashboard screen"
```

---

### Task 5: Preferences Credits Entry

**Files:**
- Modify: `features/preferences/impl/build.gradle.kts`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootState.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenter.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenterTest.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`

- [ ] **Step 1: Write Preferences tests**

Add presenter tests:

- `present - credit balance starts loading then becomes loaded`
- `present - credit balance failure becomes unavailable`

Add entry point test:

- `test credits billing nav target creates balance credits node`
- `test credits usage nav target creates daily usage credits node`

Add view tests:

- loaded card shows `$` prefix
- unavailable card shows `$0.00`
- Billing click calls supplied callback
- Usage click calls supplied callback

- [ ] **Step 2: Run failing Preferences tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*PreferencesRootPresenterTest' --tests '*PreferencesRootViewTest' --tests '*DefaultPreferencesEntryPointTest'
```

Expected: failures for missing credits state and navigation.

- [ ] **Step 3: Wire Preferences**

Add:

```kotlin
implementation(projects.features.credits.api)
implementation(projects.libraries.chatbot.api)
testImplementation(projects.features.credits.test)
testImplementation(projects.libraries.chatbot.test)
```

Add state:

```kotlin
sealed interface CreditBalanceLoadState {
    data object Loading : CreditBalanceLoadState
    data class Loaded(val balanceUsd: String) : CreditBalanceLoadState
    data object Unavailable : CreditBalanceLoadState
}
```

Add root callbacks:

```kotlin
fun navigateToCreditsBilling()
fun navigateToCreditsUsage()
fun navigateToCreditsTopUp()
```

Inject `CreditsEntryPoint` into `PreferencesFlowNode`, add `NavTarget.Credits(initialTab: CreditsEntryPoint.CreditsTab)`, and route Billing to `Balance`, Usage to `DailyUsage`. For top-up, keep callback as no-op until `credits-topup`.

- [ ] **Step 4: Run Preferences tests**

Expected: the focused Preferences tests pass.

- [ ] **Step 5: Commit Preferences integration**

```bash
git add features/preferences
git commit -m "feat: add credits settings entry"
```

---

### Task 6: Final Verification

**Files:**
- Inspect: `features/credits`
- Inspect: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl`

- [ ] **Step 1: Run focused credits tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run compile checks**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:compileDebugKotlin :features:preferences:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run app assemble**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run forbidden dependency scan**

```bash
rg -n "libraries\\.rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|UnsealUI|UnsealAgent|UnsealMiniApp|stripe|Stripe" features/credits features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl
```

Expected: no output.

- [ ] **Step 5: Commit verification note if docs changed**

If no source files changed during verification, do not create an empty commit. If a verification doc is added, commit:

```bash
git add docs/superpowers/plans/2026-06-09-credits-dashboard.md
git commit -m "docs: verify credits dashboard migration"
```

---

## Self-Review

- Spec coverage: tasks cover feature modules, entry point, three initial tabs, first-appear loads, daily range reload, analytics period reload, ledger load-more, formatting, Preferences card states, Preferences Billing/Usage navigation, top-up callback seam, tests, compile, assemble, and forbidden dependency scan.
- Explicit exclusions preserved: Stripe checkout, pull-to-refresh, persistence, Rust SDK changes, MiniApp/vault/voice/rich renderer dependencies.
- Type consistency: public tab type is `CreditsEntryPoint.CreditsTab`; presenter state and Preferences navigation use the same enum. API data models are existing `CreditBalance`, `CreditLedgerItem`, `CreditDailyUsageResponse`, and `AnalyticsTokensResponse`.
