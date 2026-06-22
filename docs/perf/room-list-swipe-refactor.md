# Room List swipe-actions refactor (perf)

Status: **proposed — not yet implemented**. Coordinate before editing the `features/home/impl` module.

## Why

Room-list scrolling janks. On-device measurement (OnePlus PHK110, 60 Hz, GPU idle at 3–8 ms → UI-thread bound) isolated the cause via A/B:

| Build | Janky frames | p95 | p99 |
|-------|--------------|-----|-----|
| Current (per-row swipe wrapper) | ~10 % | 38–200 ms | 150–550 ms |
| `swipeActionsEnabled = false` (no wrapper) | **4.5 %** | **26 ms** | **53 ms** |

The cost is **per-row swipe machinery** in `RoomSummaryRow.SwipeableRoomActions`: every visible row mounts a `draggable` + `animateFloatAsState` + `rememberCoroutineScope` + `LaunchedEffect` + two nested `Box`es + `absoluteOffset` + `background`. During a scroll the `LazyColumn` composes/measures/lays-out/draws all of this for every row that enters the viewport — even though no swipe is happening.

Two cheap fixes were tried and **rejected** (measured):
- Removing only the animation/effect/scope from idle rows but keeping `draggable`+Boxes → no improvement (10.2 %). The layout/input nodes are the cost, not the animation.
- Gating the whole wrapper on `!lazyListState.isScrollInProgress` → **worse** (52 %, p99 600 ms): toggling at every scroll start/stop recomposes all visible rows to add/remove the wrapper at once — a recomposition storm at each scroll boundary.

Disabling the feature is off the table (the swipe-to-action is part of the product experience). So the only path that keeps the feature *and* is smooth is to remove the per-row gesture nodes — i.e. move gesture detection to the list level.

## Goal

Idle rows render their content directly (the proven-smooth `swipeActionsEnabled=false` shape: **zero** per-row gesture/animation/Box nodes). Only the single row currently being swiped/open reads an offset from shared state and draws the action buttons. Target: room-list scroll back to ~4.5 % / p99 ~50 ms with swipe-to-action preserved.

## The hard part is the state machine, not the perf direction

Collapsing per-row local swipe state into **one shared state** is where the bugs live: open/drag/reorder/scroll boundaries must be fully specified, or rows mis-render. The design below pins those down. Two product rules make the single-shared-state model coherent:

- **At most one row is ever offset.** "Open" = the active row, settled at `-revealPx`. There is no "A stays open while B is dragged".
- **Identity is `RoomListRoomSummary.id` (String) everywhere** — never mix with `roomId.value`. They happen to be equal in the preview provider but are two different fields; the existing opened-swipe state already keys on `room.id` (`RoomSummaryRow.kt:244`). Action *events* still use `room.roomId` (the `RoomId`) as their payload — that's unchanged.

### Active-swipe state (a single object, hoisted to `RoomsViewList`)
```kotlin
@Immutable
data class ActiveSwipe(
    val id: String,                              // RoomListRoomSummary.id — the UI identity
    val revealPx: Float,                         // actionWidth.toPx() * actions.size, from the HIT row
    val actions: ImmutableList<RoomListItemAction>,
    val settledOpen: Boolean,                    // true once it has settled at -revealPx
)

var activeSwipe by remember { mutableStateOf<ActiveSwipe?>(null) }
val swipeOffset = remember { Animatable(0f) }    // the single offset; Animatable's MutatorMutex serialises
val swipeScope = rememberCoroutineScope()        // one scope drives snapTo (drag) and animateTo (settle)
```
`revealPx` and `actions` are **carried in the state from the hit row**, not hardcoded — reveal width depends on that row's action count (`actionWidth * swipeActions.size`, `RoomSummaryRow.kt:232`; currently 3 but render-model-derived and can vary). `actionWidth` stays a constant.

### One list-level gesture
Wrap the `LazyColumn` in a `Box`:
```kotlin
Modifier.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { pos ->
            val hit = resolveSwipeTarget(pos.y, lazyListState.layoutInfo, headerItemCount, summaries)
            val acts = hit?.let { renderSwipeActions(it) }.orEmpty()   // same actions the render model would produce
            if (hit == null || acts.isEmpty()) { activeSwipe = null; return@detectHorizontalDragGestures }
            val revealPx = actionWidthPx * acts.size
            // Hand-off: if we grabbed the already-open row, continue from -reveal; otherwise this is a
            // fresh row — the previously-active/open row stops being active (it re-renders plain, i.e.
            // snaps closed) and we start from 0. snapTo cancels any in-flight settle on the Animatable.
            val grabbedOpenRow = activeSwipe?.id == hit.id && activeSwipe?.settledOpen == true
            activeSwipe = ActiveSwipe(hit.id, revealPx, acts.toImmutableList(), settledOpen = grabbedOpenRow)
            swipeScope.launch { swipeOffset.snapTo(if (grabbedOpenRow) -revealPx else 0f) }
        },
        onHorizontalDrag = { change, dx ->
            val a = activeSwipe ?: return@detectHorizontalDragGestures
            change.consume()
            swipeScope.launch { swipeOffset.snapTo((swipeOffset.value + dx).coerceIn(-a.revealPx - 16f, 10f)) }
        },
        onDragEnd = {
            val a = activeSwipe ?: return@detectHorizontalDragGestures
            val open = -swipeOffset.value > a.revealPx * 0.35f
            activeSwipe = a.copy(settledOpen = open)
            swipeScope.launch {
                swipeOffset.animateTo(if (open) -a.revealPx else 0f)
                if (!open) activeSwipe = null                          // fully closed → drop active
            }
        },
        onDragCancel = {
            swipeScope.launch { swipeOffset.animateTo(0f); activeSwipe = null }
        },
    )
}
```
Notes:
- **Coroutine / cancellation:** every `snapTo`/`animateTo` runs on the single `swipeScope` against the single `swipeOffset`. `Animatable` uses a `MutatorMutex`, so a newer `snapTo`/`animateTo` **cancels** the in-flight one — this is exactly what makes "release row A, then immediately drag row B" safe (B's `snapTo(0)` cancels A's settle). Do not introduce a second `Animatable` or scope.
- `detectHorizontalDragGestures` only fires past the horizontal touch slop and does not consume vertical motion → the `LazyColumn`'s vertical scroll is unaffected.

### Close the open row on scroll (single effect, list level — NOT per-row)
```kotlin
LaunchedEffect(lazyListState.isScrollInProgress) {
    if (lazyListState.isScrollInProgress && activeSwipe != null) {
        activeSwipe = null
        swipeOffset.snapTo(0f)
    }
}
```
This clears **only** the one active row (it re-renders plain). It must **not** re-add/remove a wrapper across all rows — that was the rejected `isScrollInProgress`-gating approach that caused the 52 % recomposition storm. Here idle rows never had a wrapper, so only one row changes.

### Hit-test as a pure, unit-tested function
```kotlin
// Pure: no Compose, no side effects. Unit-test this.
fun resolveSwipeTarget(
    y: Float,
    layoutInfo: LazyListLayoutInfo,        // pass visibleItemsInfo + viewport in tests via a small fake
    headerItemCount: Int,                  // number of leading non-room item{} blocks
    summaries: List<RoomListRoomSummary>,
): RoomListRoomSummary? {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset until (it.offset + it.size) }
        ?: return null
    val roomIndex = item.index - headerItemCount
    return summaries.getOrNull(roomIndex)   // null for banner / non-room / out-of-range index
}
```
- `item.offset` from `visibleItemsInfo` already accounts for `contentPadding` and scroll position, so no manual padding math.
- `headerItemCount` is computed from the **same** banner-visibility flags that gate the leading `item{}` blocks (`RoomListContentView.kt:238` — currently 0 or 1 leading item). Extract that count into a single `@Composable`-free helper used both for rendering decisions (if practical) and for the hit-test, so they cannot drift.
- Deliberately **no** `key` on the room `itemsIndexed` (keys reintroduce the scroll-jump-on-reorder bug). Identity comes from `index - headerItemCount → summaries[index]`.

### Per-row rendering (`RoomSummaryRow`, replaces `SwipeableRoomActions`)
```kotlin
RoomSummaryDisplayType.ROOM -> {
    val active = activeSwipe?.takeIf { it.id == room.id }
    if (active != null) {
        Box {                                                 // only the active row: buttons + offset
            ActionButtonsRow(actions = active.actions, ...)   // reuse SwipeActionButton / swipeActionTitle
            Box(Modifier.offset { IntOffset(swipeOffset.value.roundToInt(), 0) }.background(bgCanvas)) { rowContent() }
        }
    } else {
        rowContent()                                          // every other row: plain content, zero nodes
    }
}
```

### Accepted behaviours (document, don't treat as bugs)
- Only one row open at a time; grabbing/opening B **snaps** a settled-open A closed (single shared offset can't animate two rows). Acceptable and common for list swipe.
- Scrolling closes the open row (snaps).

## Files
- `features/home/impl/.../components/RoomListContentView.kt` (**currently WIP — coordinate**): `ActiveSwipe` state, gesture `Box`, scroll-close effect, `headerItemCount`/`resolveSwipeTarget`, thread `activeSwipe` + `swipeOffset` to rows.
- `features/home/impl/.../components/RoomSummaryRow.kt` (clean): replace `SwipeableRoomActions` with active-row-only visual; keep `SwipeActionButton` / `swipeActionTitle`; extract `ActionButtonsRow`.
- `features/home/impl/.../<somewhere testable>`: `resolveSwipeTarget` + `headerItemCount` helper (pure Kotlin, no Compose) so they can be unit-tested.

## Risks / test points
1. **State-machine boundaries** (the real risk): hand-off A-open→drag-B, drag-cancel, double-tap, reorder mid-drag, scroll-during-drag. Covered by the rules above + UI tests below.
2. **Hit-test mapping** across header banners — `headerItemCount` must match rendered leading items in every banner-visibility combination → **unit-tested**, not hand-tested.
3. **Horizontal vs vertical gesture coordination** — vertical scroll smooth; horizontal swipe triggers; diagonal edge cases.
4. **Coroutine/Animatable** — single scope + single `Animatable` (MutatorMutex) only; no second animator.
5. **List reorder mid-swipe** (new message moves the active row) — active row resolves by `id`; on reorder it may scroll away → acceptable to close.
6. **Concurrent edits** to the `home` module by other agents during the refactor.

## Validation
**Unit tests (pure logic):**
- `resolveSwipeTarget`: y in a room → that summary; y in a leading banner → null; y in a non-room/footer item → null; y past the last item / negative → null; with `headerItemCount = 0` and `= 1`.
- `headerItemCount` helper across banner-visibility flag combinations.

**Compose UI tests:**
- Swipe the first room → its action(s) appear; tap Mark read/unread → the corresponding `RoomListEvent` is emitted.
- Open A, then swipe B → A is closed and B is active.
- With a leading banner present, swiping the first *room* hits the correct room (off-by-one guard).
- Scroll starts while a row is open → the open row closes.

**On-device:**
- `dumpsys gfxinfo <pkg>` while scrolling → target ~4.5 % janky / p99 ~50 ms (matching the `swipeActionsEnabled=false` A/B).

## Fallback
If the hit-test proves too fragile, add a lightweight per-row `Modifier.onGloballyPositioned` to record row bounds into a shared map (cheaper than a gesture node, but still one modifier per row — measure whether it stays within budget).

## Related
- The room-list `LazyColumn` intentionally has **no** `key` on `itemsIndexed` (scroll-stability on reorder); do not add one.
- A separate, already-applied fix caches `CardTransforms.transform` in `ToolCardDispatcher.ToolCard` (`remember(rawData, cardType)`) — unrelated to this refactor.
