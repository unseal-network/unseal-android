# Game Card Click → MiniApp WebView — Android Design

**Status:** Ready to implement  
**Date:** 2026-06-12  
**Spec owner:** Unseal Android team

---

## Overview

Tapping a game message card in the timeline must open the MiniApp WebView full-screen.  
The `MiniAppNode` + `MiniAppView` stack already exists and is reachable from  
`MessagesFlowNode.NavTarget.MiniApp`.  The only missing pieces are:

1. `remoteUrl` is not carried on `TimelineItemGameContent` or `TimelineEvent.OpenGame`.  
2. `TimelinePresenter` handles `OpenGame` as a log-only stub.

This document describes the three-file change needed to close both gaps.

---

## Data-flow today

```
TimelineItemGameView ──eventSink──► TimelinePresenter (OpenGame)
                                          │
                                          └── Timber.d(...)   ← stub, never navigates
```

## Data-flow after this change

```
GameMessageContentParser
  reads "m.game.info.remote_url" from Matrix event JSON
  ↓
TimelineItemGameContent.remoteUrl: String?
  ↓
TimelineEvent.OpenGame(gameId, gameRoomId, remoteUrl)
  ↓
TimelinePresenter
  calls navigator.navigateToMiniApp(gameId.toLong(), remoteUrl, gameRoomId)
  ↓
MessagesFlowNode.NavTarget.MiniApp(appId, remoteUrl, meetId)  ← already wired
  ↓
MiniAppNode / MiniAppView                                      ← already implemented
```

---

## Matrix event JSON shape

The Unseal server embeds the WebView URL inside `m.game.info.remote_url` when sending a game invite:

```json
{
  "msgtype": "m.game.v1",
  "body": "邀请大家...",
  "m.game.roomid": "<game-server-room-id>",
  "m.game.creator": "@alice:matrix.example.com",
  "m.game.info": {
    "id": 42,
    "name": "Dev-Werewolf",
    "brief": "一个角色扮演游戏",
    "icon": "https://...",
    "remote_url": "https://games.unseal.network/werewolf/room/abc123"
  }
}
```

The `remote_url` key is **already written** by `DefaultGameApiService.sendGameInviteMessage()` (line 159):  
`if (gameInfo.remoteUrl != null) put("remote_url", gameInfo.remoteUrl)`.

---

## Parameter mapping

| WebView parameter | Source |
|:--- |:--- |
| `appId` | `TimelineItemGameContent.gameId` (cast to `Long`) |
| `remoteUrl` | `TimelineItemGameContent.remoteUrl` (from `m.game.info.remote_url`) |
| `meetId` | `TimelineItemGameContent.gameRoomId` (the game-server room ID, not a Matrix ID) |

---

## Required changes

### 1 — `TimelineItemGameContent.kt`

**File:** `features/messages/impl/src/.../timeline/model/event/TimelineItemGameContent.kt`

Add one nullable field after `gameId`:

```kotlin
/** WebView URL for this game session. Null when the server omitted remote_url. */
val remoteUrl: String?,
```

> The field is nullable to stay backward-compatible with older messages that were sent before  
> `remote_url` was included. When null the card remains tappable; the WebView falls back to an  
> empty URL and the host app may show an error state.

---

### 2 — `GameMessageContentParser.kt`

**File:** `features/messages/impl/src/.../timeline/factories/event/GameMessageContentParser.kt`

Read `remote_url` from `gameInfoObj` and include it in the returned content:

```kotlin
return TimelineItemGameContent(
    gameName      = gameInfoObj?.string("name") ?: fallbackBody.ifBlank { "Game" },
    gameBrief     = gameInfoObj?.string("brief")?.takeIf { it.isNotBlank() },
    resolvedIconUrl = resolvedIconUrl,
    homeserverHost  = homeserverHost,
    gameRoomId    = gameRoomId,
    gameId        = gameInfoObj?.int("id") ?: 0,
    remoteUrl     = gameInfoObj?.string("remote_url"),   // ← NEW
    creatorUserId = content.string("m.game.creator"),
    fallbackBody  = fallbackBody,
)
```

No other changes are needed in this file; the `string()` helper extension already handles missing keys gracefully.

---

### 3 — `TimelineEvent.kt`

**File:** `features/messages/impl/src/.../timeline/TimelineEvent.kt`

Extend `OpenGame` to carry the URL:

```kotlin
/**
 * User tapped the game card in the timeline — open the MiniApp WebView.
 *
 * @param gameId     Numeric Unseal game ID (maps to MiniApp appId).
 * @param gameRoomId Game-server room ID (maps to MiniApp meetId).
 * @param remoteUrl  WebView URL from [TimelineItemGameContent.remoteUrl].
 *                   Null when the message predates remote_url support.
 */
data class OpenGame(
    val gameId: Int,
    val gameRoomId: String,
    val remoteUrl: String?,
) : TimelineItemEvent
```

---

### 4 — `TimelineItemGameView.kt`

**File:** `features/messages/impl/src/.../timeline/components/event/TimelineItemGameView.kt`

Pass `remoteUrl` when firing the event (one-line change):

```kotlin
eventSink(
    TimelineEvent.OpenGame(
        gameId    = content.gameId,
        gameRoomId = content.gameRoomId,
        remoteUrl = content.remoteUrl,   // ← NEW
    )
)
```

---

### 5 — `TimelinePresenter.kt`

**File:** `features/messages/impl/src/.../timeline/TimelinePresenter.kt`

Replace the log-only stub with a real navigation call:

```kotlin
is TimelineEvent.OpenGame -> {
    navigator.navigateToMiniApp(
        appId     = event.gameId.toLong(),
        remoteUrl = event.remoteUrl,
        meetId    = event.gameRoomId,
    )
}
```

No other changes; `MessagesNavigator.navigateToMiniApp` is already declared and implemented  
in `MessagesNode` (calls `MessagesFlowNode.navigateToMiniApp`) and forwarded to  
`MessagesFlowNode.NavTarget.MiniApp`.

---

## Unchanged components

| Component | Why no change needed |
|:--- |:--- |
| `MessagesNavigator` | Already has `navigateToMiniApp(appId, remoteUrl, meetId)` |
| `MessagesNode` | Already delegates to `MessagesFlowNode` |
| `MessagesFlowNode` | Already has `NavTarget.MiniApp` and pushes it onto the backstack |
| `MiniAppNode` | Already reads `Inputs(appId, remoteUrl, meetId)` from the backstack |
| `MiniAppView` | Already loads `config.url` in the WebView |
| `ThreadedMessagesNode` | Already has a no-op stub; threaded rooms do not host game cards |

---

## Edge cases

### `remoteUrl` is null
Older game invite messages sent before the server included `remote_url` will produce  
`remoteUrl = null`. The backstack push still happens:  
`NavTarget.MiniApp(appId, remoteUrl = "", meetId)` (coerced in `MessagesFlowNode`).  
`MiniAppView` calls `webView.loadUrl("")` which renders a blank page.

For a better user experience the WebView could be pre-populated with a fallback error screen
or the presenter could show a snackbar and skip navigation when `remoteUrl` is null.
That is an optional polish step and is not required for the initial implementation.

### Game card in a thread
`ThreadedMessagesNode.navigateToMiniApp` is a no-op (already in place). Game cards
are not expected to appear in threads; this guard prevents crashes if they ever do.

---

## Test checklist

| Scenario | Expected result |
|:--- |:--- |
| Tap game card — `remote_url` present | `MiniAppNode` opens, WebView loads the game URL |
| Tap game card — `remote_url` absent | `MiniAppNode` opens, WebView shows blank / error |
| Tap game card — offline | Navigation proceeds; WebView shows network error page |
| Back press in MiniApp | `MiniAppHostBridge.closeApp()` → `navigateUp()` returns to timeline |
| Game card in thread | Tap is ignored (no-op); no crash |

Unit tests to add or update:

- `TimelinePresenterTest` — assert `navigateToMiniApp` is called with correct `appId`/`remoteUrl`/`meetId` when `OpenGame` event fires.
- `GameMessageContentParserTest` — assert `remoteUrl` is parsed from `m.game.info.remote_url`; assert null when key is absent.

---

## Preview update

Update the `TimelineItemGameContent` sample data in `TimelineItemGameView.kt` preview calls
to include the new `remoteUrl` field:

```kotlin
content = TimelineItemGameContent(
    ...
    remoteUrl = "https://games.unseal.network/werewolf/preview",
    ...
)
```

---

## Implementation order

1. `TimelineItemGameContent` — add field  
2. `GameMessageContentParser` — populate field  
3. `TimelineEvent.OpenGame` — add field  
4. `TimelineItemGameView` — pass field to event  
5. `TimelinePresenter` — replace stub with `navigator.navigateToMiniApp`  
6. Fix preview sample data  
7. Run unit tests  

All five source changes are in `features/messages/impl`; no cross-module API changes are required.
