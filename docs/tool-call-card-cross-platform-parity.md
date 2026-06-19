# Tool Call Card Stream UI and Performance Plan

## Goal

Align Android agent stream rendering with the iOS interaction model while keeping the chat timeline performant with many historical and active streams.

This plan intentionally separates normal timeline text, empty assistant stream loading, and tool-call cards. A tool-call card must only appear after a recognizable tool event exists.

## Final Stream States

1. Empty stream
   - Assistant event has started.
   - No text token has arrived.
   - No recognizable tool event has arrived.
   - Render a lightweight iOS-like cursor/typing indicator directly in timeline content.
   - Do not render any tool card, card shell, message bubble, or placeholder card.

2. Text stream
   - Text/markdown tokens have arrived.
   - Render as normal timeline text/markdown content.
   - Do not wrap in a message card or speech bubble.
   - Do not create a tool card unless a recognizable tool event arrives.

3. Tool started
   - A tool event has arrived and exposes a recognizable display name, such as Hotels or Flights.
   - Render the tool-call root card with that tool name and a pending/skeleton body.
   - If the tool name cannot be resolved yet, keep the empty/text stream state and do not show a generic card.

4. Partial tool UI
   - Tool props are partially renderable.
   - Render real card content as soon as it is stable enough.
   - Keep loading animation local to the current card body or unfinished tab.
   - Do not refresh the whole root card on each new chunk.

5. Final tool UI
   - Stream is terminal: completed, failed, or cancelled.
   - Stop loading animations.
   - Freeze markdown and card rendering into stable mode.
   - Completed tool cards default to collapsed for timeline performance.

## UI Rules

- There is no "message bubble" or "message card" wrapper for agent output.
- Tool-call cards are timeline content blocks, inserted only when the stream contains a recognizable tool call.
- Multiple tool calls render as one root card with tabs/chips.
- Only the selected tab body is rendered.
- Header progress reflects mixed state:
  - done count
  - error count
  - calling count
  - proportional/mixed status instead of a single red error state for partial failures.
- Light and dark mode must use Element/Material3 semantic tokens. Avoid hard-coded colors that only work in dark mode.
- Shared card visuals should live in the shared kit, not in each card:
  - surface
  - header
  - progress/status
  - tabs/chips
  - dividers
  - section containers
  - loading indicators

## Interaction Rules

- During streaming, the selected tab may auto-follow the latest calling tool until the user manually selects a tab.
- Once the user manually selects a tab, do not auto-jump again for that root card.
- Remember expanded/collapsed state by `ToolCallRootRenderModel.id` for the current room timeline session.
- Completed cards default to collapsed.
- If a user expands a completed card, keep it expanded while the root id remains in the current timeline session.

## Markdown Rendering Rules

There are two markdown paths:

1. AI SDK stream markdown
   - Only unfinished stream markdown uses streaming rendering.
   - Prefer updating only the currently streaming final markdown part.
   - Completed markdown parts should switch to stable mode.
   - Terminal streams should freeze all markdown as stable content.

2. Normal room message markdown
   - Treat as stable content from the beginning.
   - Optimize by caching parse/render state and skipping repeated `setMarkdown` calls.
   - Preserve final formatting quality.

Streaming markdown can prioritize smoothness over full formatting fidelity while the stream is still running. Stable markdown should prioritize correctness and visual parity.

## Performance Rules

- UI updates from stream snapshots should be throttled or coalesced, targeting roughly one update per frame or every 50-100 ms.
- Only the latest snapshot in the window should render.
- Timeline item identity must remain stable:
  - keep `LazyColumn` item keys stable
  - keep AI stream part keys stable by `part.id`
- Do not compose hidden tab bodies.
- Do not parse JSON props for hidden tabs.
- Do not load hidden tab images.
- Cache tool card parsing/transforms by `entry.id + propsHash`.
- Cache stable markdown by full source text or render version.
- Avoid infinite animations outside visible, active, unfinished stream content.
- Completed/historical streams should be static.
- Give pending shells, media areas, and partial-loading rows stable dimensions to reduce layout remeasurement and scroll jumps.

## Android Implementation Areas

Primary files:

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/MarkdownBody.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardKit.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapter.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcher.kt`

Secondary card-specific files should only receive local layout cleanup where they must adopt shared surfaces or fixed-size content areas.

## Implementation Shape

1. Add explicit stream render state derivation in the AI renderer:
   - empty stream
   - text stream
   - named tool pending
   - partial tool UI
   - final UI

2. Update tool root rendering:
   - no root card without a recognizable tool name
   - completed default collapsed
   - expanded/collapsed memory by root id
   - manual tab selection disables auto-follow for that root id
   - render only selected tab body

3. Update markdown rendering:
   - stable markdown path for normal room messages and completed stream blocks
   - streaming path only for unfinished AI stream markdown
   - avoid repeated markdown parse/set work when source text and theme inputs are unchanged

4. Add caches:
   - markdown render/parse cache for stable content
   - tool props parse/transform cache by entry id and props hash

5. Add performance verification:
   - active long stream with markdown
   - active tool stream with partial card updates
   - timeline with many completed tool cards
   - light/dark mode switch
   - manual tab selection during streaming

## Open Decisions

No product decisions remain open as of this plan.

Implementation details that can be decided locally:

- exact throttle interval within 50-100 ms
- in-memory cache size limits
- whether expanded-state memory lives inside `TimelineItemAiView` or a small room-scoped holder

