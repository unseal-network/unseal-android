# Room Schedules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native Android Room Schedules feature matching the iOS room AI configuration flow for schedules, working memory, cron editing, and room-screen schedule badge behavior.

**Architecture:** Add a dedicated `features/roomschedules` feature family with API, implementation, and test modules. Keep Chatbot API calls behind the existing `ChatbotApiService`, keep Matrix room membership and permission checks behind `JoinedRoom`, and integrate with `features/messages` through a narrow entry-point callback.

**Tech Stack:** Kotlin, Jetpack Compose, Appyx nodes, Metro dependency injection, Molecule/Turbine presenter tests, Matrix API abstractions, Chatbot API service, Gradle.

---

## File Structure

Create:

- `features/roomschedules/api/build.gradle.kts`: API module Gradle config.
- `features/roomschedules/api/src/main/kotlin/io/element/android/features/roomschedules/api/RoomSchedulesEntryPoint.kt`: public entry point and callbacks.
- `features/roomschedules/impl/build.gradle.kts`: implementation module Gradle config.
- `features/roomschedules/test/build.gradle.kts`: test helper module Gradle config.
- `features/roomschedules/test/src/main/kotlin/io/element/android/features/roomschedules/test/FakeRoomSchedulesEntryPoint.kt`: fake entry point for host tests.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/DefaultRoomSchedulesEntryPoint.kt`: entry point implementation.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/RoomSchedulesFlowNode.kt`: Appyx flow for config and edit screens.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/cron/CronParser.kt`: cron model, parser, formatter.
- `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/cron/CronParserTest.kt`: cron behavior tests.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/model/ScheduleFormatters.kt`: schedule enabled/id/agent Matrix ID helpers.
- `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/model/ScheduleFormattersTest.kt`: schedule helper tests.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesEvents.kt`: Room AI config events.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesNavigator.kt`: Room AI config navigation seam.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesState.kt`: Room AI config state.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesPresenter.kt`: schedule and working-memory presenter.
- `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesPresenterTest.kt`: presenter tests.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditEvents.kt`: edit events.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditMode.kt`: create/edit mode.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditNavigator.kt`: edit navigation seam.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditState.kt`: edit state.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditPresenter.kt`: create/edit presenter.
- `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditPresenterTest.kt`: edit presenter tests.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesNode.kt`: config node.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesView.kt`: config Compose UI.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditNode.kt`: edit node.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditView.kt`: edit Compose UI and cron controls.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgeEvents.kt`: room badge events.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgeState.kt`: room badge state.
- `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgePresenter.kt`: presenter for active count and has-agent-in-room.
- `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgePresenterTest.kt`: badge tests.

Modify:

- `features/messages/api/src/main/kotlin/io/element/android/features/messages/api/MessagesEntryPoint.kt`: add schedules navigation callback.
- `features/messages/impl/build.gradle.kts`: depend on `features.roomschedules.api`.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/MessagesNode.kt`: create and pass room schedule badge state to the room UI.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/MessagesView.kt`: show schedules action.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/MessagesState.kt`: include room schedule badge state when the existing presenter/state boundary requires it.
- `appnav/src/main/kotlin/io/element/android/appnav/room/joined/JoinedRoomLoadedFlowNode.kt`: inject and open `RoomSchedulesEntryPoint`.

Do not modify:

- `libraries/rustsdk/**`
- Matrix Rust SDK generated bindings
- voice, vault, sandbox, MiniApp, or rich renderer modules
- Chatbot API endpoint definitions. Existing schedule and working-memory methods are already present and are the API boundary for this feature.

---

### Task 1: Feature Module Shell And Entry Point

**Files:**
- Create: `features/roomschedules/api/build.gradle.kts`
- Create: `features/roomschedules/api/src/main/kotlin/io/element/android/features/roomschedules/api/RoomSchedulesEntryPoint.kt`
- Create: `features/roomschedules/impl/build.gradle.kts`
- Create: `features/roomschedules/test/build.gradle.kts`
- Create: `features/roomschedules/test/src/main/kotlin/io/element/android/features/roomschedules/test/FakeRoomSchedulesEntryPoint.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/DefaultRoomSchedulesEntryPoint.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/RoomSchedulesFlowNode.kt`

- [ ] **Step 1: Write entry-point API**

Create `RoomSchedulesEntryPoint.kt`:

```kotlin
package io.element.android.features.roomschedules.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.parcelize.Parcelize

interface RoomSchedulesEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object RoomAiConfig : InitialTarget
    }

    data class Params(
        val roomId: RoomId,
        val roomName: String,
        val joinedRoom: JoinedRoom,
        val initialTarget: InitialTarget = InitialTarget.RoomAiConfig,
    ) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onSchedulesChanged()
    }
}
```

- [ ] **Step 2: Add Gradle files**

Create `features/roomschedules/api/build.gradle.kts`:

```kotlin
plugins {
    id("io.element.android-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.roomschedules.api"
}

dependencies {
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.matrix.api)
}
```

Create `features/roomschedules/impl/build.gradle.kts`:

```kotlin
import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.roomschedules.impl"

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
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    api(projects.features.roomschedules.api)

    testCommonDependencies(libs, true)
    testImplementation(projects.features.roomschedules.test)
    testImplementation(projects.libraries.chatbot.test)
    testImplementation(projects.libraries.matrix.test)
}
```

Create `features/roomschedules/test/build.gradle.kts`:

```kotlin
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.features.roomschedules.test"
}

dependencies {
    implementation(projects.features.roomschedules.api)
    implementation(projects.libraries.architecture)
    implementation(projects.tests.testutils)
}
```

- [ ] **Step 3: Add fake entry point**

Create `FakeRoomSchedulesEntryPoint.kt`:

```kotlin
package io.element.android.features.roomschedules.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.roomschedules.api.RoomSchedulesEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeRoomSchedulesEntryPoint(
    var createNodeResult: (Node, BuildContext, RoomSchedulesEntryPoint.Params, RoomSchedulesEntryPoint.Callback) -> Node = { _, _, _, _ -> lambdaError() },
) : RoomSchedulesEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: RoomSchedulesEntryPoint.Params,
        callback: RoomSchedulesEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, params, callback)
    }
}
```

- [ ] **Step 4: Add implementation entry point and temporary flow node**

Create `DefaultRoomSchedulesEntryPoint.kt`:

```kotlin
package io.element.android.features.roomschedules.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.Inject
import io.element.android.features.roomschedules.api.RoomSchedulesEntryPoint
import io.element.android.libraries.architecture.createNode

@Inject
class DefaultRoomSchedulesEntryPoint : RoomSchedulesEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: RoomSchedulesEntryPoint.Params,
        callback: RoomSchedulesEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<RoomSchedulesFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback),
        )
    }
}
```

Create `RoomSchedulesFlowNode.kt` with a temporary compile-safe view:

```kotlin
package io.element.android.features.roomschedules.impl

import android.os.Parcelable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class RoomSchedulesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext, plugins = plugins) {
    @Parcelize
    private data object TemporaryTarget : Parcelable

    @Composable
    override fun View(modifier: Modifier) {
        Text("Room Schedules", modifier = modifier)
    }
}
```

- [ ] **Step 5: Run compile to verify module inclusion**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:compileDebugKotlin
```

Expected: PASS. The settings script auto-includes the new feature modules because their build files exist.

- [ ] **Step 6: Commit**

```sh
git add features/roomschedules
git commit -m "feat: add room schedules feature shell"
```

---

### Task 2: Cron And Schedule Helper Behavior

**Files:**
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/cron/CronParser.kt`
- Create: `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/cron/CronParserTest.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/model/ScheduleFormatters.kt`
- Create: `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/model/ScheduleFormattersTest.kt`

- [ ] **Step 1: Write failing cron parser tests**

Create `CronParserTest.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.cron

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CronParserTest {
    @Test
    fun `toCron - writes all iOS supported modes`() {
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.Workdays, hour = 9, minute = 5, intervalHours = 2, weekday = 2)))
            .isEqualTo("cron(05 09 ? * 2-6 *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.EveryDay, hour = 9, minute = 0, intervalHours = 2, weekday = 2)))
            .isEqualTo("cron(00 09 ? * * *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.EveryNHours, hour = 9, minute = 5, intervalHours = 6, weekday = 2)))
            .isEqualTo("cron(0 */6 ? * * *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.EveryHourAtMinute, hour = 9, minute = 15, intervalHours = 1, weekday = 2)))
            .isEqualTo("cron(15 */1 ? * * *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.Weekday, hour = 18, minute = 30, intervalHours = 1, weekday = 1)))
            .isEqualTo("cron(30 18 ? * 1 *)")
    }

    @Test
    fun `toReadable - formats iOS supported expressions`() {
        assertThat(CronParser.toReadable("cron(0 9 ? * 2-6 *)")).isEqualTo("Mon-Fri at 09:00")
        assertThat(CronParser.toReadable("cron(15 */6 ? * * *)")).isEqualTo("Every 6h at :15")
        assertThat(CronParser.toReadable("cron(0 */2 ? * * *)")).isEqualTo("Every 2h")
        assertThat(CronParser.toReadable("cron(30 18 ? * * *)")).isEqualTo("Every day at 18:30")
        assertThat(CronParser.toReadable("cron(30 18 ? * 1,3,5 *)")).isEqualTo("Mon, Wed, Fri at 18:30")
        assertThat(CronParser.toReadable("not-cron")).isEqualTo("not-cron")
    }

    @Test
    fun `toPickerModel - parses iOS supported expressions and falls back to default`() {
        assertThat(CronParser.toPickerModel("cron(0 9 ? * 2-6 *)")).isEqualTo(CronPickerModel(mode = CronPickerMode.Workdays, hour = 9, minute = 0, intervalHours = 1, weekday = 2))
        assertThat(CronParser.toPickerModel("cron(0 */6 ? * * *)")).isEqualTo(CronPickerModel(mode = CronPickerMode.EveryNHours, hour = 9, minute = 0, intervalHours = 6, weekday = 2))
        assertThat(CronParser.toPickerModel("cron(15 */1 ? * * *)")).isEqualTo(CronPickerModel(mode = CronPickerMode.EveryHourAtMinute, hour = 9, minute = 15, intervalHours = 1, weekday = 2))
        assertThat(CronParser.toPickerModel("cron(30 18 ? * 1 *)")).isEqualTo(CronPickerModel(mode = CronPickerMode.Weekday, hour = 18, minute = 30, intervalHours = 1, weekday = 1))
        assertThat(CronParser.toPickerModel("cron(30 18 ? * * *)")).isEqualTo(CronPickerModel(mode = CronPickerMode.EveryDay, hour = 18, minute = 30, intervalHours = 1, weekday = 2))
        assertThat(CronParser.toPickerModel("bad")).isEqualTo(CronPickerModel.Default)
    }
}
```

- [ ] **Step 2: Run cron tests to verify failure**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest --tests '*CronParserTest'
```

Expected: FAIL because `CronParser`, `CronPickerMode`, and `CronPickerModel` are not defined.

- [ ] **Step 3: Implement cron parser**

Create `CronParser.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.cron

enum class CronPickerMode {
    Workdays,
    EveryDay,
    EveryNHours,
    EveryHourAtMinute,
    Weekday,
}

data class CronPickerModel(
    val mode: CronPickerMode,
    val hour: Int,
    val minute: Int,
    val intervalHours: Int,
    val weekday: Int,
) {
    companion object {
        val Default = CronPickerModel(
            mode = CronPickerMode.EveryDay,
            hour = 9,
            minute = 0,
            intervalHours = 2,
            weekday = 2,
        )
    }
}

object CronParser {
    fun toReadable(expression: String): String {
        val fields = extractFields(expression)
        if (fields.size < 5) return expression
        val minuteField = fields[0]
        val hourField = fields[1]
        val dowField = fields[4]
        if (hourField.startsWith("*/")) {
            val interval = hourField.drop(2)
            val minuteSuffix = if (minuteField == "0") "" else " at :${minuteField.toIntOrNull().orZero().zeroPadded()}"
            return "Every ${interval}h$minuteSuffix"
        }
        val hour = hourField.toIntOrNull().orZero()
        val minute = minuteField.toIntOrNull().orZero()
        val time = "${hour.zeroPadded()}:${minute.zeroPadded()}"
        if (dowField == "*" || dowField == "?") {
            return "Every day at $time"
        }
        val days = parseDow(dowField)
        return if (days.isEmpty()) "Every day at $time" else "$days at $time"
    }

    fun toCron(model: CronPickerModel): String {
        return when (model.mode) {
            CronPickerMode.Workdays -> "cron(${model.minute.zeroPadded()} ${model.hour.zeroPadded()} ? * 2-6 *)"
            CronPickerMode.EveryDay -> "cron(${model.minute.zeroPadded()} ${model.hour.zeroPadded()} ? * * *)"
            CronPickerMode.EveryNHours -> "cron(0 */${model.intervalHours.coerceAtLeast(1)} ? * * *)"
            CronPickerMode.EveryHourAtMinute -> "cron(${model.minute.zeroPadded()} */1 ? * * *)"
            CronPickerMode.Weekday -> "cron(${model.minute.zeroPadded()} ${model.hour.zeroPadded()} ? * ${model.weekday} *)"
        }
    }

    fun toPickerModel(expression: String): CronPickerModel {
        val fields = extractFields(expression)
        if (fields.size < 5) return CronPickerModel.Default
        val minuteField = fields[0]
        val hourField = fields[1]
        val dowField = fields[4]
        val minute = minuteField.toIntOrNull().orZero()
        val hour = hourField.toIntOrNull() ?: 9
        if (hourField.startsWith("*/")) {
            val interval = hourField.drop(2).toIntOrNull() ?: 1
            return CronPickerModel(CronPickerMode.EveryNHours, hour = hour, minute = minute, intervalHours = interval, weekday = 2)
        }
        if (minuteField.startsWith("*/") || hourField == "*/1") {
            return CronPickerModel(CronPickerMode.EveryHourAtMinute, hour = hour, minute = minute, intervalHours = 1, weekday = 2)
        }
        if (dowField == "2-6") {
            return CronPickerModel(CronPickerMode.Workdays, hour = hour, minute = minute, intervalHours = 1, weekday = 2)
        }
        if (dowField != "*" && dowField != "?") {
            return CronPickerModel(CronPickerMode.Weekday, hour = hour, minute = minute, intervalHours = 1, weekday = dowField.toIntOrNull() ?: 2)
        }
        return CronPickerModel(CronPickerMode.EveryDay, hour = hour, minute = minute, intervalHours = 1, weekday = 2)
    }

    private fun extractFields(expression: String): List<String> {
        var inner = expression.trim()
        if (inner.lowercase().startsWith("cron(") && inner.endsWith(")")) {
            inner = inner.drop(5).dropLast(1)
        }
        return inner.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun parseDow(dow: String): String {
        val names = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        if ("-" in dow) {
            val parts = dow.split("-")
            val from = parts.getOrNull(0)?.toIntOrNull()
            val to = parts.getOrNull(1)?.toIntOrNull()
            if (from != null && to != null && from >= 1 && to <= 7 && from <= to) {
                if (from == 2 && to == 6) return "Mon-Fri"
                return (from..to).mapNotNull { names.getOrNull(it) }.joinToString(", ")
            }
        }
        if ("," in dow) {
            return dow.split(",").mapNotNull { names.getOrNull(it.toIntOrNull() ?: -1) }.joinToString(", ")
        }
        return names.getOrNull(dow.toIntOrNull() ?: -1).orEmpty()
    }
}

private fun Int?.orZero() = this ?: 0
private fun Int.zeroPadded(): String = toString().padStart(2, '0')
```

- [ ] **Step 4: Write failing schedule helper tests**

Create `ScheduleFormattersTest.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.model

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import org.junit.Test

class ScheduleFormattersTest {
    @Test
    fun `isEnabled - status takes precedence and missing fields default disabled`() {
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = "enabled", enabled = false).isEnabled()).isTrue()
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = "disabled", enabled = true).isEnabled()).isFalse()
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = null, enabled = true).isEnabled()).isTrue()
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = null, enabled = null).isEnabled()).isFalse()
    }

    @Test
    fun `stableId - uses schedule id then name`() {
        assertThat(aChatbotSchedule(scheduleId = "schedule-id").stableId()).isEqualTo("schedule-id")
        assertThat(aChatbotSchedule(scheduleId = null).copy(name = "Daily").stableId()).isEqualTo("Daily")
    }

    @Test
    fun `matrixUserId - builds from localpart and server name with bot fallback`() {
        assertThat(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com").matrixUserId()).isEqualTo("@agent:example.com")
        assertThat(ChatbotAgent(botName = "bot", localpart = null, serverName = "example.com").matrixUserId()).isEqualTo("bot")
    }
}
```

- [ ] **Step 5: Implement schedule helpers**

Create `ScheduleFormatters.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.model

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule

fun ChatbotSchedule.stableId(): String = scheduleId ?: name

fun ChatbotSchedule.isEnabled(): Boolean {
    return when (status) {
        "enabled" -> true
        "disabled" -> false
        else -> enabled ?: false
    }
}

fun ChatbotSchedule.withEnabledStatus(enabled: Boolean): ChatbotSchedule {
    return copy(status = if (enabled) "enabled" else "disabled", enabled = null)
}

fun ChatbotAgent.matrixUserId(): String {
    val local = localpart
    val server = serverName
    return if (!local.isNullOrBlank() && !server.isNullOrBlank()) {
        "@$local:$server"
    } else {
        botName
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest --tests '*CronParserTest' --tests '*ScheduleFormattersTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```sh
git add features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/cron features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/cron features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/model features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/model
git commit -m "feat: add room schedule cron helpers"
```

---

### Task 3: Room AI Configuration Presenter

**Files:**
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesEvents.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesNavigator.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesState.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesPresenter.kt`
- Create: `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesPresenterTest.kt`

- [ ] **Step 1: Write failing presenter tests**

Create `RoomSchedulesPresenterTest.kt` with these test names and assertions:

```kotlin
package io.element.android.features.roomschedules.impl.config

import com.google.common.truth.Truth.assertThat
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RoomSchedulesPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads schedules and working memory once`() = runTest {
        var scheduleCalls = 0
        var memoryCalls = 0
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                scheduleCalls++
                Result.success(listOf(aChatbotSchedule(scheduleId = "one").copy(status = "enabled", creatorId = A_SESSION_ID.value)))
            }
            getRoomWorkingMemoryResult = {
                memoryCalls++
                Result.success("memory")
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.isNotEmpty() && it.workingMemory == "memory" && !it.isLoadingSchedules && !it.isLoadingMemory }
            assertThat(loaded.schedules.single().stableId).isEqualTo("one")
            assertThat(scheduleCalls).isEqualTo(1)
            assertThat(memoryCalls).isEqualTo(1)
            loaded.eventSink(RoomSchedulesEvents.OnAppear)
            assertThat(scheduleCalls).isEqualTo(1)
            assertThat(memoryCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state - displayed schedules hide disabled schedules from other users and sort enabled first`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(
                    listOf(
                        aChatbotSchedule(scheduleId = "other-disabled").copy(status = "disabled", creatorId = "@other:server"),
                        aChatbotSchedule(scheduleId = "mine-disabled").copy(status = "disabled", creatorId = A_SESSION_ID.value),
                        aChatbotSchedule(scheduleId = "mine-enabled").copy(status = "enabled", creatorId = A_SESSION_ID.value),
                    )
                )
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.size == 3 && !it.isLoadingSchedules }
            assertThat(loaded.displayedSchedules.map { it.stableId }).containsExactly("mine-enabled", "mine-disabled").inOrder()
            assertThat(loaded.activeCount).isEqualTo(1)
            loaded.eventSink(RoomSchedulesEvents.ShowOnlyMineChanged(true))
            val mine = awaitStateWhere { it.showOnlyMine }
            assertThat(mine.displayedSchedules.map { it.stableId }).containsExactly("mine-enabled", "mine-disabled").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - toggle schedule optimistically updates and rolls back by reloading on failure`() = runTest {
        var statusRequest: Pair<String, String>? = null
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(listOf(aChatbotSchedule(scheduleId = "toggle").copy(status = "enabled", creatorId = A_SESSION_ID.value)))
            }
            updateScheduleStatusResult = { id, status ->
                statusRequest = id to status
                Result.failure(RuntimeException("network"))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.singleOrNull()?.isEnabled() == true }
            loaded.eventSink(RoomSchedulesEvents.ToggleSchedule(loaded.schedules.single()))
            val failed = awaitStateWhere { it.scheduleError?.contains("network") == true && it.schedules.single().isEnabled() }
            assertThat(statusRequest).isEqualTo("toggle" to "disabled")
            assertThat(failed.schedules.single().isEnabled()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - delete confirms then removes local schedule on success`() = runTest {
        val deleted = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = { Result.success(listOf(aChatbotSchedule(scheduleId = "delete").copy(creatorId = A_SESSION_ID.value))) }
            deleteScheduleResult = {
                deleted += it
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.size == 1 }
            loaded.eventSink(RoomSchedulesEvents.RequestDeleteSchedule(loaded.schedules.single()))
            val confirming = awaitStateWhere { it.deleteConfirmationScheduleId == "delete" }
            confirming.eventSink(RoomSchedulesEvents.ConfirmDeleteSchedule)
            val deletedState = awaitStateWhere { it.schedules.isEmpty() && it.deleteConfirmationScheduleId == null }
            assertThat(deleted).containsExactly("delete")
            assertThat(deletedState.displayedSchedules).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - working memory save and cancel preserve iOS behavior`() = runTest {
        val saved = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            getRoomWorkingMemoryResult = { Result.success("old") }
            updateRoomWorkingMemoryResult = { _, content ->
                saved += content
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.workingMemory == "old" }
            loaded.eventSink(RoomSchedulesEvents.StartEditingMemory)
            val editing = awaitStateWhere { it.isEditingMemory && it.editingMemoryText == "old" }
            editing.eventSink(RoomSchedulesEvents.EditingMemoryChanged("new"))
            val changed = awaitStateWhere { it.editingMemoryText == "new" }
            changed.eventSink(RoomSchedulesEvents.SaveMemory)
            val savedState = awaitStateWhere { !it.isEditingMemory && it.workingMemory == "new" }
            assertThat(saved).containsExactly("new")
            savedState.eventSink(RoomSchedulesEvents.StartEditingMemory)
            val editingAgain = awaitStateWhere { it.isEditingMemory }
            editingAgain.eventSink(RoomSchedulesEvents.CancelEditingMemory)
            awaitStateWhere { !it.isEditingMemory && it.editingMemoryText.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        room: FakeJoinedRoom = FakeJoinedRoom(),
        navigator: FakeRoomSchedulesNavigator = FakeRoomSchedulesNavigator(),
    ): RoomSchedulesPresenter {
        return RoomSchedulesPresenter(
            roomId = A_ROOM_ID,
            roomName = "Room",
            joinedRoom = room,
            chatbotApiService = service,
            navigator = navigator,
        )
    }
}

private class FakeRoomSchedulesNavigator : RoomSchedulesNavigator {
    val created = mutableListOf<Unit>()
    val edited = mutableListOf<String>()
    var doneCalls = 0
    var changedCalls = 0

    override fun onCreateSchedule() {
        created += Unit
    }

    override fun onEditSchedule(scheduleId: String) {
        edited += scheduleId
    }

    override fun onDone() {
        doneCalls++
    }

    override fun onSchedulesChanged() {
        changedCalls++
    }
}
```

- [ ] **Step 2: Run presenter tests to verify failure**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest --tests '*RoomSchedulesPresenterTest'
```

Expected: FAIL because config presenter types do not exist.

- [ ] **Step 3: Implement config state and events**

Create `RoomSchedulesEvents.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.config

import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule

sealed interface RoomSchedulesEvents {
    data object OnAppear : RoomSchedulesEvents
    data object Refresh : RoomSchedulesEvents
    data class SelectTab(val tab: RoomSchedulesTab) : RoomSchedulesEvents
    data class ShowOnlyMineChanged(val showOnlyMine: Boolean) : RoomSchedulesEvents
    data object CreateSchedule : RoomSchedulesEvents
    data class EditSchedule(val schedule: ChatbotSchedule) : RoomSchedulesEvents
    data class ToggleSchedule(val schedule: ChatbotSchedule) : RoomSchedulesEvents
    data class RequestDeleteSchedule(val schedule: ChatbotSchedule) : RoomSchedulesEvents
    data object ConfirmDeleteSchedule : RoomSchedulesEvents
    data object DismissDeleteConfirmation : RoomSchedulesEvents
    data object RefreshMemory : RoomSchedulesEvents
    data object StartEditingMemory : RoomSchedulesEvents
    data class EditingMemoryChanged(val text: String) : RoomSchedulesEvents
    data object CancelEditingMemory : RoomSchedulesEvents
    data object SaveMemory : RoomSchedulesEvents
    data object ClearError : RoomSchedulesEvents
    data object Dismiss : RoomSchedulesEvents
}
```

Create `RoomSchedulesNavigator.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.config

interface RoomSchedulesNavigator {
    fun onCreateSchedule()
    fun onEditSchedule(scheduleId: String)
    fun onDone()
    fun onSchedulesChanged()
}
```

Create `RoomSchedulesState.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.config

import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

enum class RoomSchedulesTab {
    Schedules,
    WorkingMemory,
}

data class RoomSchedulesState(
    val roomId: RoomId,
    val roomName: String,
    val currentUserId: String,
    val selectedTab: RoomSchedulesTab = RoomSchedulesTab.Schedules,
    val schedules: ImmutableList<ChatbotSchedule> = emptyList<ChatbotSchedule>().toImmutableList(),
    val isLoadingSchedules: Boolean = false,
    val showOnlyMine: Boolean = false,
    val scheduleError: String? = null,
    val workingMemory: String = "",
    val editingMemoryText: String = "",
    val isEditingMemory: Boolean = false,
    val isLoadingMemory: Boolean = false,
    val isSavingMemory: Boolean = false,
    val canEditMemory: Boolean = true,
    val memoryError: String? = null,
    val deleteConfirmationScheduleId: String? = null,
    val eventSink: (RoomSchedulesEvents) -> Unit,
) {
    val displayedSchedules: List<ChatbotSchedule> = schedules
        .filter { it.isEnabled() || it.creatorId == currentUserId }
        .filter { !showOnlyMine || it.creatorId == currentUserId }
        .sortedByDescending { it.isEnabled() }

    val activeCount: Int = displayedSchedules.count { it.isEnabled() }
    val deleteConfirmationSchedule: ChatbotSchedule? = schedules.firstOrNull { it.stableId() == deleteConfirmationScheduleId }
}
```

- [ ] **Step 4: Implement presenter**

Create `RoomSchedulesPresenter.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.features.roomschedules.impl.model.withEnabledStatus
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.powerlevels.canEditRolesAndPermissions
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

class RoomSchedulesPresenter @AssistedInject constructor(
    @Assisted private val roomId: RoomId,
    @Assisted private val roomName: String,
    @Assisted private val joinedRoom: JoinedRoom,
    private val chatbotApiService: ChatbotApiService,
    @Assisted private val navigator: RoomSchedulesNavigator,
) : Presenter<RoomSchedulesState> {
    @AssistedFactory
    interface Factory {
        fun create(
            roomId: RoomId,
            roomName: String,
            joinedRoom: JoinedRoom,
            navigator: RoomSchedulesNavigator,
        ): RoomSchedulesPresenter
    }

    @Composable
    override fun present(): RoomSchedulesState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(RoomSchedulesTab.Schedules) }
        var schedules by remember { mutableStateOf(emptyList<ChatbotSchedule>()) }
        var isLoadingSchedules by remember { mutableStateOf(false) }
        var showOnlyMine by remember { mutableStateOf(false) }
        var scheduleError by remember { mutableStateOf<String?>(null) }
        var workingMemory by remember { mutableStateOf("") }
        var editingMemoryText by remember { mutableStateOf("") }
        var isEditingMemory by remember { mutableStateOf(false) }
        var isLoadingMemory by remember { mutableStateOf(false) }
        var isSavingMemory by remember { mutableStateOf(false) }
        var canEditMemory by remember { mutableStateOf(true) }
        var memoryError by remember { mutableStateOf<String?>(null) }
        var deleteConfirmationScheduleId by remember { mutableStateOf<String?>(null) }

        fun loadSchedules() = coroutineScope.launch {
            isLoadingSchedules = true
            chatbotApiService.listSchedules(roomId.value)
                .onSuccess {
                    schedules = it
                    scheduleError = null
                }
                .onFailure {
                    scheduleError = it.message ?: it.toString()
                }
            isLoadingSchedules = false
        }

        fun loadMemory() = coroutineScope.launch {
            isLoadingMemory = true
            chatbotApiService.getRoomWorkingMemory(roomId.value)
                .onSuccess {
                    workingMemory = it
                    memoryError = null
                }
                .onFailure {
                    memoryError = it.message ?: it.toString()
                }
            isLoadingMemory = false
        }

        fun checkPermission() = coroutineScope.launch {
            canEditMemory = joinedRoom.roomPermissions()
                .fold(
                    onSuccess = { it.use { permissions -> permissions.canEditRolesAndPermissions() } },
                    onFailure = { true },
                )
        }

        fun toggle(schedule: ChatbotSchedule) = coroutineScope.launch {
            val id = schedule.stableId()
            val newEnabled = !schedule.isEnabled()
            schedules = schedules.map { if (it.stableId() == id) it.withEnabledStatus(newEnabled) else it }
            chatbotApiService.updateScheduleStatus(id, if (newEnabled) "enabled" else "disabled")
                .onSuccess {
                    scheduleError = null
                    navigator.onSchedulesChanged()
                }
                .onFailure {
                    scheduleError = it.message ?: it.toString()
                    chatbotApiService.listSchedules(roomId.value).onSuccess { reloaded -> schedules = reloaded }
                }
        }

        fun deleteConfirmed() = coroutineScope.launch {
            val id = deleteConfirmationScheduleId ?: return@launch
            chatbotApiService.deleteSchedule(id)
                .onSuccess {
                    schedules = schedules.filterNot { it.stableId() == id }
                    deleteConfirmationScheduleId = null
                    scheduleError = null
                    navigator.onSchedulesChanged()
                }
                .onFailure {
                    scheduleError = it.message ?: it.toString()
                }
        }

        fun saveMemory() = coroutineScope.launch {
            isSavingMemory = true
            chatbotApiService.updateRoomWorkingMemory(roomId.value, editingMemoryText)
                .onSuccess {
                    workingMemory = editingMemoryText
                    editingMemoryText = ""
                    isEditingMemory = false
                    memoryError = null
                }
                .onFailure {
                    memoryError = it.message ?: it.toString()
                }
            isSavingMemory = false
        }

        fun handleEvent(event: RoomSchedulesEvents) {
            when (event) {
                RoomSchedulesEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadSchedules()
                    loadMemory()
                    checkPermission()
                }
                RoomSchedulesEvents.Refresh -> if (selectedTab == RoomSchedulesTab.Schedules) loadSchedules() else loadMemory()
                is RoomSchedulesEvents.SelectTab -> selectedTab = event.tab
                is RoomSchedulesEvents.ShowOnlyMineChanged -> showOnlyMine = event.showOnlyMine
                RoomSchedulesEvents.CreateSchedule -> navigator.onCreateSchedule()
                is RoomSchedulesEvents.EditSchedule -> navigator.onEditSchedule(event.schedule.stableId())
                is RoomSchedulesEvents.ToggleSchedule -> toggle(event.schedule)
                is RoomSchedulesEvents.RequestDeleteSchedule -> deleteConfirmationScheduleId = event.schedule.stableId()
                RoomSchedulesEvents.ConfirmDeleteSchedule -> deleteConfirmed()
                RoomSchedulesEvents.DismissDeleteConfirmation -> deleteConfirmationScheduleId = null
                RoomSchedulesEvents.RefreshMemory -> loadMemory()
                RoomSchedulesEvents.StartEditingMemory -> {
                    editingMemoryText = workingMemory
                    isEditingMemory = true
                }
                is RoomSchedulesEvents.EditingMemoryChanged -> editingMemoryText = event.text
                RoomSchedulesEvents.CancelEditingMemory -> {
                    editingMemoryText = ""
                    isEditingMemory = false
                }
                RoomSchedulesEvents.SaveMemory -> saveMemory()
                RoomSchedulesEvents.ClearError -> {
                    scheduleError = null
                    memoryError = null
                }
                RoomSchedulesEvents.Dismiss -> {
                    navigator.onSchedulesChanged()
                    navigator.onDone()
                }
            }
        }

        return RoomSchedulesState(
            roomId = roomId,
            roomName = roomName,
            currentUserId = joinedRoom.sessionId.value,
            selectedTab = selectedTab,
            schedules = schedules.toImmutableList(),
            isLoadingSchedules = isLoadingSchedules,
            showOnlyMine = showOnlyMine,
            scheduleError = scheduleError,
            workingMemory = workingMemory,
            editingMemoryText = editingMemoryText,
            isEditingMemory = isEditingMemory,
            isLoadingMemory = isLoadingMemory,
            isSavingMemory = isSavingMemory,
            canEditMemory = canEditMemory,
            memoryError = memoryError,
            deleteConfirmationScheduleId = deleteConfirmationScheduleId,
            eventSink = ::handleEvent,
        )
    }
}
```

- [ ] **Step 5: Run config tests**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest --tests '*RoomSchedulesPresenterTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```sh
git add features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/config
git commit -m "feat: add room schedules presenter"
```

---

### Task 4: Schedule Edit Presenter

**Files:**
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditEvents.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditMode.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditNavigator.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditState.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditPresenter.kt`
- Create: `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditPresenterTest.kt`

- [ ] **Step 1: Write failing edit presenter tests**

Create `ScheduleEditPresenterTest.kt` with these required test names:

```kotlin
package io.element.android.features.roomschedules.impl.edit

import com.google.common.truth.Truth.assertThat
import io.element.android.features.roomschedules.impl.cron.CronPickerMode
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ScheduleEditPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - create mode loads agents and selects first agent`() = runTest {
        val service = FakeChatbotApiService().apply {
            listAgentsResult = { Result.success(listOf(agent("alpha"), agent("beta"))) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.OnAppear)
            val loaded = awaitStateWhere { it.agents.size == 2 && it.selectedAgentBotName == "alpha" }
            assertThat(loaded.title).isEqualTo("New Schedule")
            assertThat(loaded.isCreate).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - edit mode seeds immutable name and agent plus editable action and cron`() = runTest {
        val schedule = aChatbotSchedule(scheduleId = "edit").copy(name = "Daily", agentId = "agent", action = "Do work", cron = "cron(15 10 ? * * *)")
        val presenter = createPresenter(mode = ScheduleEditMode.Edit(schedule))

        presenter.test {
            val state = awaitItem()
            assertThat(state.name).isEqualTo("Daily")
            assertThat(state.selectedAgentBotName).isEqualTo("agent")
            assertThat(state.action).isEqualTo("Do work")
            assertThat(state.cronModel).isEqualTo(CronPickerModel(CronPickerMode.EveryDay, hour = 10, minute = 15, intervalHours = 1, weekday = 2))
            assertThat(state.isCreate).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - submit validates create fields`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.Submit)
            val missingName = awaitStateWhere { it.error == "Schedule name cannot be empty" }
            missingName.eventSink(ScheduleEditEvents.NameChanged("Daily"))
            val named = awaitStateWhere { it.name == "Daily" }
            named.eventSink(ScheduleEditEvents.Submit)
            val missingAction = awaitStateWhere { it.error == "Action cannot be empty" }
            assertThat(missingAction.error).isEqualTo("Action cannot be empty")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create builds full agent matrix id and saves`() = runTest {
        val requests = mutableListOf<String>()
        val navigator = FakeScheduleEditNavigator()
        val service = FakeChatbotApiService().apply {
            listAgentsResult = { Result.success(listOf(agent("bot", localpart = "agent", serverName = "example.com"))) }
            createScheduleResult = {
                requests += "${it.agentId}|${it.name}|${it.action}|${it.roomId}"
                Result.success(io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleResponse(success = true, ebScheduleId = "ok"))
            }
        }
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.OnAppear)
            val loaded = awaitStateWhere { it.selectedAgentBotName == "bot" }
            loaded.eventSink(ScheduleEditEvents.NameChanged(" Daily "))
            val named = awaitStateWhere { it.name == " Daily " }
            named.eventSink(ScheduleEditEvents.ActionChanged(" Work "))
            val action = awaitStateWhere { it.action == " Work " }
            action.eventSink(ScheduleEditEvents.Submit)
            awaitStateWhere { navigator.savedCalls == 1 && !it.isSubmitting }
            assertThat(requests.single()).isEqualTo("@agent:example.com|Daily|Work|${A_ROOM_ID.value}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - edit sends cron action and timezone only`() = runTest {
        val updates = mutableListOf<String>()
        val navigator = FakeScheduleEditNavigator()
        val service = FakeChatbotApiService().apply {
            updateScheduleResult = { id, request ->
                updates += "$id|${request.action}|${request.cron}|${request.timezone.isNotBlank()}"
                Result.success(io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleResponse(success = true, ebScheduleId = "ok"))
            }
        }
        val schedule = aChatbotSchedule(scheduleId = "edit").copy(action = "Old", cron = "cron(00 09 ? * * *)")
        val presenter = createPresenter(mode = ScheduleEditMode.Edit(schedule), service = service, navigator = navigator)

        presenter.test {
            val state = awaitItem()
            state.eventSink(ScheduleEditEvents.ActionChanged("New"))
            val changed = awaitStateWhere { it.action == "New" }
            changed.eventSink(ScheduleEditEvents.Submit)
            awaitStateWhere { navigator.savedCalls == 1 }
            assertThat(updates.single()).startsWith("edit|New|cron(")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        mode: ScheduleEditMode = ScheduleEditMode.Create,
        service: FakeChatbotApiService = FakeChatbotApiService(),
        room: FakeJoinedRoom = FakeJoinedRoom(),
        navigator: FakeScheduleEditNavigator = FakeScheduleEditNavigator(),
    ): ScheduleEditPresenter {
        return ScheduleEditPresenter(
            mode = mode,
            roomId = A_ROOM_ID,
            joinedRoom = room,
            chatbotApiService = service,
            navigator = navigator,
        )
    }

    private fun agent(botName: String, localpart: String? = botName, serverName: String? = "example.com") =
        ChatbotAgent(botName = botName, localpart = localpart, serverName = serverName)

    private fun joinedMember(userId: String) = RoomMember(
        userId = UserId(userId),
        displayName = null,
        avatarUrl = null,
        membership = RoomMembershipState.JOIN,
        isNameAmbiguous = false,
        powerLevel = 0,
        isIgnored = false,
        role = RoomMember.Role.User,
        membershipChangeReason = null,
        isServiceMember = false,
    )
}

private class FakeScheduleEditNavigator : ScheduleEditNavigator {
    var savedCalls = 0
    var cancelledCalls = 0
    override fun onSaved() {
        savedCalls++
    }
    override fun onCancelled() {
        cancelledCalls++
    }
}
```

- [ ] **Step 2: Run edit presenter tests to verify failure**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest --tests '*ScheduleEditPresenterTest'
```

Expected: FAIL because edit presenter types do not exist.

- [ ] **Step 3: Implement edit mode, events, state, and navigator**

Create the edit files with these public signatures:

```kotlin
sealed interface ScheduleEditMode : Parcelable {
    @Parcelize data object Create : ScheduleEditMode
    @Parcelize data class Edit(val schedule: ChatbotSchedule) : ScheduleEditMode
}

sealed interface ScheduleEditEvents {
    data object OnAppear : ScheduleEditEvents
    data class NameChanged(val name: String) : ScheduleEditEvents
    data class AgentChanged(val botName: String) : ScheduleEditEvents
    data class ActionChanged(val action: String) : ScheduleEditEvents
    data class CronModelChanged(val model: CronPickerModel) : ScheduleEditEvents
    data object Submit : ScheduleEditEvents
    data object Cancel : ScheduleEditEvents
    data object ClearError : ScheduleEditEvents
}

interface ScheduleEditNavigator {
    fun onSaved()
    fun onCancelled()
}
```

Create `ScheduleEditState` with:

```kotlin
data class ScheduleEditState(
    val mode: ScheduleEditMode,
    val agents: ImmutableList<ChatbotAgent>,
    val joinedMemberIds: ImmutableSet<String>,
    val isSubmitting: Boolean,
    val name: String,
    val selectedAgentBotName: String,
    val action: String,
    val cronModel: CronPickerModel,
    val error: String?,
    val eventSink: (ScheduleEditEvents) -> Unit,
) {
    val isCreate: Boolean = mode is ScheduleEditMode.Create
    val title: String = if (isCreate) "New Schedule" else "Edit Schedule"
    val selectedAgentIsInRoom: Boolean = selectedAgentBotName.isBlank() ||
        joinedMemberIds.isEmpty() ||
        agents.firstOrNull { it.botName == selectedAgentBotName }?.matrixUserId()?.let { it in joinedMemberIds } != false
}
```

- [ ] **Step 4: Implement edit presenter**

Create `ScheduleEditPresenter.kt` with:

```kotlin
class ScheduleEditPresenter @AssistedInject constructor(
    @Assisted private val mode: ScheduleEditMode,
    @Assisted private val roomId: RoomId,
    @Assisted private val joinedRoom: JoinedRoom,
    private val chatbotApiService: ChatbotApiService,
    @Assisted private val navigator: ScheduleEditNavigator,
) : Presenter<ScheduleEditState>
```

Implementation requirements:

- Use `rememberCoroutineScope` and `remember` state.
- Seed edit mode from `mode.schedule`.
- On `OnAppear`, call `chatbotApiService.listAgents()` and `joinedRoom.getMembers(limit = Int.MAX_VALUE)` or `joinedRoom.updateMembers()` plus `membersStateFlow.value`, then keep only `RoomMembershipState.JOIN`.
- In create mode, select the first loaded Agent when no Agent is selected.
- Validate `name.trim()`, `action.trim()`, and selected Agent.
- Block submit with `"Agent not in room"` when `joinedMemberIds` is not empty and `selectedAgentIsInRoom` is false.
- Use `TimeZone.getDefault().id` for timezone.
- Create request uses full Agent Matrix ID from `matrixUserId()`.
- Edit request uses `stableId()` and only sends cron/action/timezone.
- On save success call `navigator.onSaved()`.
- On cancel call `navigator.onCancelled()`.

- [ ] **Step 5: Run edit tests**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest --tests '*ScheduleEditPresenterTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```sh
git add features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/edit
git commit -m "feat: add schedule edit presenter"
```

---

### Task 5: Appyx Flow Nodes And Compose Screens

**Files:**
- Modify: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/RoomSchedulesFlowNode.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesNode.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/config/RoomSchedulesView.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditNode.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/edit/ScheduleEditView.kt`

- [ ] **Step 1: Replace temporary flow node with backstack flow**

Implement `RoomSchedulesFlowNode` following `SkillsFlowNode`:

- BackStack initial element is `NavTarget.RoomAiConfig`.
- `NavTarget.EditSchedule(mode: ScheduleEditMode)`.
- Room config callback pushes create/edit targets.
- Edit callback pops and calls config node reload through a small `reloadToken` or by navigating back and letting config presenter refresh through `onSchedulesChanged`.
- Close calls `RoomSchedulesEntryPoint.Callback.onDone()`.
- Schedule changes call `RoomSchedulesEntryPoint.Callback.onSchedulesChanged()`.

- [ ] **Step 2: Add config node**

Create `RoomSchedulesNode.kt`:

```kotlin
@ContributesNode(SessionScope::class)
@AssistedInject
class RoomSchedulesNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: RoomSchedulesPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val roomId: RoomId, val roomName: String, val joinedRoom: JoinedRoom) : Plugin
    interface Callback : Plugin {
        fun onDone()
        fun onCreateSchedule()
        fun onEditSchedule(schedule: ChatbotSchedule)
        fun onSchedulesChanged()
    }
}
```

The node creates `RoomSchedulesNavigator` and renders `RoomSchedulesView(state = presenter.present())`.

- [ ] **Step 3: Add edit node**

Create `ScheduleEditNode.kt`:

```kotlin
@ContributesNode(SessionScope::class)
@AssistedInject
class ScheduleEditNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ScheduleEditPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val mode: ScheduleEditMode, val roomId: RoomId, val joinedRoom: JoinedRoom) : Plugin
    interface Callback : Plugin {
        fun onSaved()
        fun onCancelled()
    }
}
```

The node creates `ScheduleEditNavigator` and renders `ScheduleEditView(state = presenter.present())`.

- [ ] **Step 4: Add config Compose view**

Create `RoomSchedulesView.kt` with:

- `LaunchedEffect(Unit)` sending `OnAppear`.
- Header row with back/dismiss, title `"Room AI Config"`, subtitle `state.roomName`.
- Two buttons or segmented controls for `"Schedules"` and `"Working Memory"`.
- Schedules tab:
  - loading indicator;
  - active count text;
  - Mine toggle;
  - `LazyColumn` schedule rows;
  - empty text and add button;
  - owner-only buttons: Edit, Enable/Disable, Delete.
- Working memory tab:
  - loading indicator;
  - read-only monospaced `Text`;
  - `OutlinedTextField` in edit mode;
  - Add/Edit, Cancel, Save, Refresh buttons.
- Delete confirmation can be rendered as inline confirmation controls to avoid introducing a dialog API in the first pass.

- [ ] **Step 5: Add edit Compose view**

Create `ScheduleEditView.kt` with:

- `LaunchedEffect(Unit)` sending `OnAppear`.
- Header with cancel and title.
- Create-mode name `OutlinedTextField`.
- Edit-mode read-only name `Text`.
- Create-mode Agent selector using a simple list of buttons or dropdown if nearby code already has a stable dropdown component.
- Edit-mode read-only Agent text.
- Warning text `"This agent is not in the current room. Please invite it first."` when `!state.selectedAgentIsInRoom`.
- Multiline action `OutlinedTextField`.
- Cron controls:
  - mode buttons for all five modes;
  - hour and minute controls using simple `OutlinedTextField` or compact buttons;
  - interval controls for 1, 2, 3, 4, 6, 8, 12;
  - weekday controls for iOS weekday values.
- Preview text from `CronParser.toReadable(CronParser.toCron(state.cronModel))`.
- Submit button with loading text.

- [ ] **Step 6: Compile feature**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Run all roomschedules unit tests**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```sh
git add features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl
git commit -m "feat: add room schedules screens"
```

---

### Task 6: Room Badge Presenter And Messages Integration

**Files:**
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgeEvents.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgeState.kt`
- Create: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgePresenter.kt`
- Create: `features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgePresenterTest.kt`
- Modify: `features/messages/api/src/main/kotlin/io/element/android/features/messages/api/MessagesEntryPoint.kt`
- Modify: `features/messages/impl/build.gradle.kts`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/MessagesPresenter.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/MessagesView.kt`
- Modify: app navigation entry-point wiring files if compile errors show the callback must be routed there.

- [ ] **Step 1: Write failing badge presenter tests**

Create `RoomScheduleBadgePresenterTest.kt`:

```kotlin
package io.element.android.features.roomschedules.impl.room

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RoomScheduleBadgePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads active schedule count and has agent in room`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(
                    listOf(
                        aChatbotSchedule(scheduleId = "enabled").copy(status = "enabled"),
                        aChatbotSchedule(scheduleId = "disabled").copy(status = "disabled"),
                    )
                )
            }
            listAgentsResult = {
                Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com")))
            }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@agent:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.isVisible }
            assertThat(loaded.activeScheduleCount).isEqualTo(1)
            assertThat(loaded.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - hides schedules when no agent is joined`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = { Result.success(listOf(aChatbotSchedule(scheduleId = "enabled").copy(status = "enabled"))) }
            listAgentsResult = { Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com"))) }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@someone:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading }
            assertThat(loaded.isVisible).isFalse()
            assertThat(loaded.activeScheduleCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - failure hides button and stores error`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = { Result.failure(RuntimeException("network")) }
            listAgentsResult = { Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com"))) }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@agent:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.error?.contains("network") == true }
            assertThat(loaded.isVisible).isFalse()
            assertThat(loaded.activeScheduleCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh reloads count`() = runTest {
        var second = false
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                if (second) {
                    Result.success(
                        listOf(
                            aChatbotSchedule(scheduleId = "one").copy(status = "enabled"),
                            aChatbotSchedule(scheduleId = "two").copy(status = "enabled"),
                        )
                    )
                } else {
                    second = true
                    Result.success(emptyList())
                }
            }
            listAgentsResult = { Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com"))) }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@agent:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val empty = awaitStateWhere { !it.isLoading && it.isVisible && it.activeScheduleCount == 0 }
            empty.eventSink(RoomScheduleBadgeEvents.Refresh)
            awaitStateWhere { !it.isLoading && it.activeScheduleCount == 2 }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService,
        room: FakeJoinedRoom,
    ): RoomScheduleBadgePresenter {
        return RoomScheduleBadgePresenter(
            roomId = A_ROOM_ID,
            joinedRoom = room,
            chatbotApiService = service,
        )
    }

    private fun roomWithMembers(vararg userIds: String): FakeJoinedRoom {
        return FakeJoinedRoom().apply {
            givenRoomMembersState(RoomMembersState.Ready(userIds.map(::joinedMember)))
        }
    }

    private fun joinedMember(userId: String) = RoomMember(
        userId = UserId(userId),
        displayName = null,
        avatarUrl = null,
        membership = RoomMembershipState.JOIN,
        isNameAmbiguous = false,
        powerLevel = 0,
        isIgnored = false,
        role = RoomMember.Role.User,
        membershipChangeReason = null,
        isServiceMember = false,
    )
}
```

Use `FakeChatbotApiService`, `FakeJoinedRoom`, and the same Agent Matrix ID helper from Task 2.

- [ ] **Step 2: Implement badge presenter**

Create:

```kotlin
sealed interface RoomScheduleBadgeEvents {
    data object OnAppear : RoomScheduleBadgeEvents
    data object Refresh : RoomScheduleBadgeEvents
}

data class RoomScheduleBadgeState(
    val isLoading: Boolean,
    val isVisible: Boolean,
    val activeScheduleCount: Int,
    val error: String?,
    val eventSink: (RoomScheduleBadgeEvents) -> Unit,
)
```

`RoomScheduleBadgePresenter`:

- Assisted `roomId: RoomId` and `joinedRoom: JoinedRoom`.
- Inject `ChatbotApiService`.
- On first appear and refresh, load schedules and agents.
- Joined member IDs must include only `RoomMembershipState.JOIN`.
- `isVisible = agents.any { it.matrixUserId() in joinedMemberIds }`.
- `activeScheduleCount = schedules.count { it.isEnabled() }` only when visible; otherwise zero.
- On failure, `isVisible = false`, `activeScheduleCount = 0`, and `error` stores message.

- [ ] **Step 3: Add messages callback**

Modify `MessagesEntryPoint.Callback`:

```kotlin
fun navigateToRoomSchedules(roomId: RoomId, roomName: String, joinedRoom: JoinedRoom)
```

Update all fake/test callback implementations to add a no-op or lambda-error implementation.

- [ ] **Step 4: Wire Messages implementation**

In `features/messages/impl/build.gradle.kts`, add:

```kotlin
implementation(projects.features.roomschedules.api)
```

In the central Messages presenter/view:

- Inject or create `RoomScheduleBadgePresenter.Factory`.
- Render the state in the room action cluster.
- On click, call `callback.navigateToRoomSchedules(room.roomId, room.info().displayName ?: room.roomId.value, room)`.
- After returning from RoomSchedules entry point, call `RoomScheduleBadgeEvents.Refresh`.

If the actual room action UI lives in a subcomponent instead of `MessagesView.kt`, modify the file that owns the existing room action cluster. Use `rg -n "navigateToRoomDetails|developer|RoomCall|Pinned|composer|toolbar" features/messages/impl/src/main/kotlin -S` to locate the exact host before editing.

- [ ] **Step 5: Wire app navigation**

Modify `appnav/src/main/kotlin/io/element/android/appnav/room/joined/JoinedRoomLoadedFlowNode.kt`:

- Add import:

```kotlin
import io.element.android.features.roomschedules.api.RoomSchedulesEntryPoint
```

- Add constructor dependency after `messagesEntryPoint`:

```kotlin
private val roomSchedulesEntryPoint: RoomSchedulesEntryPoint,
```

- Add `NavTarget`:

```kotlin
@Parcelize
data object RoomSchedules : NavTarget
```

- Add `resolve` case:

```kotlin
NavTarget.RoomSchedules -> createRoomSchedulesNode(buildContext)
```

- Add helper:

```kotlin
private fun createRoomSchedulesNode(buildContext: BuildContext): Node {
    val callback = object : RoomSchedulesEntryPoint.Callback {
        override fun onDone() {
            backstack.pop()
        }

        override fun onSchedulesChanged() {
        }
    }
    return roomSchedulesEntryPoint.createNode(
        parentNode = this,
        buildContext = buildContext,
        params = RoomSchedulesEntryPoint.Params(
            roomId = inputs.room.roomId,
            roomName = inputs.room.info().displayName ?: inputs.room.roomId.value,
            joinedRoom = inputs.room,
        ),
        callback = callback,
    )
}
```

- Add implementation to the `MessagesEntryPoint.Callback` object in `createMessagesNode`:

```kotlin
override fun navigateToRoomSchedules(roomId: RoomId, roomName: String, joinedRoom: JoinedRoom) {
    backstack.push(NavTarget.RoomSchedules)
}
```

The parameters are intentionally accepted by the callback to keep `MessagesEntryPoint` independent; `JoinedRoomLoadedFlowNode` uses `inputs.room` as the authoritative joined room instance.

- [ ] **Step 6: Run integration compiles**

Run:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest :features:roomschedules:impl:compileDebugKotlin :features:messages:impl:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit**

```sh
git add features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room features/roomschedules/impl/src/test/kotlin/io/element/android/features/roomschedules/impl/room features/messages appnav
git commit -m "feat: wire room schedules entry point"
```

---

### Task 7: Final Verification And Dependency Scan

**Files:**
- Modify files touched by Tasks 1-6 when a verification command identifies a concrete compile, test, or dependency-scan failure.

- [ ] **Step 1: Run full Room Schedules tests**

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Run feature compiles**

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:compileDebugKotlin :features:messages:impl:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 3: Run Chatbot API regression tests**

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:impl:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 4: Run forbidden dependency scan**

```sh
rg -n "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|vault|Vault|sandbox|Sandbox|UnsealUI|UnsealAgent|UnsealMiniApp|miniapp|MiniApp" features/roomschedules features/messages -S
```

Expected:

- no output from `features/roomschedules`;
- no new schedules-related output from `features/messages`.

- [ ] **Step 5: Check worktree and recent commits**

```sh
git status --short --branch
git log --oneline -8
```

Expected:

- `git status` shows branch `feature/agent-management` with no modified or untracked files;
- recent commits include room schedules shell, cron helpers, presenters, screens, and messages wiring.

- [ ] **Step 6: Commit fixes if verification required changes**

If Steps 1-4 required fixes, inspect the changed files and commit them:

```sh
git status --short
git add features/roomschedules features/messages appnav
git commit -m "fix: stabilize room schedules migration"
```

Expected: no commit is needed when Steps 1-5 already pass with a clean worktree.

---

## Self-Review Checklist

- Spec coverage: Tasks 1-7 cover feature modules, cron parser, schedule filtering/toggling/delete, working memory, schedule create/edit, room badge, room integration, tests, and forbidden dependency scan.
- Dependency boundary: No task edits Rust SDK, voice, vault, sandbox, MiniApp, or Unseal component-library modules.
- Execution order: Implementation starts only after this plan exists. Each task ends with focused verification and a commit.
- Next migration item after this feature: `webhook-triggers`, but only after `room-schedules` is implemented and verified.
