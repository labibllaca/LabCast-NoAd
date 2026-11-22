# Podcast screen file split

## Intent

Make screen code easier to locate, review, and change by splitting the 5,955-line `PodcastAppContent.kt` into cohesive Compose files. Keep the app behavior and appearance unchanged. This is a maintainability refactor, not a redesign.

## Goals

- Keep `PodcastAppContent` as the composition/navigation root.
- Move existing top-level composables into screen- or feature-focused files while keeping their package and callable signatures unchanged.
- Preserve the existing uncommitted typography changes in `PodcastAppContent.kt` and do not overwrite other working-tree changes.
- Build and run relevant tests after moves; keep each move small enough to identify compile or behavior regressions.

## Non-goals and anti-goals

- No changes to screen layout, copy, state ownership, ViewModel APIs, playback behavior, or navigation.
- No new dependencies, abstraction layers, or broad UI cleanup.
- Do not split solely by line count if a group of composables is cohesive in its current feature.

## Constraints

- Single Android `:app` target; Kotlin/Jetpack Compose; current package is `com.example.ui`.
- Existing worktree has uncommitted typography/UI edits in `PodcastAppContent.kt`, `ProducerDetailScreen.kt`, `SettingsScreen.kt`, and theme files. Preserve all of it; do not stage or commit those changes.
- Keep the public composable APIs stable unless compilation proves a symbol is file-private and can safely be narrowed.

## Approaches

1. Keep the monolith and rely on navigation/search: no code risk, but no better locality.
2. Split into cohesive screen/feature files with mechanical moves: preserves the existing APIs and behavior while making edits local. **Selected; recommended for low-risk maintenance value.**
3. Redesign screen state and extract new UI modules at the same time: could improve deeper boundaries, but adds behavior risk and is outside this refactor.

## Implementation shape

Keep the root/navigation composable in `PodcastAppContent.kt`. Move screen bodies and their closely related private helpers into files grouped by feature (for example Discover, detail, downloads, history, and player). Put genuinely shared rows/artwork helpers in a shared UI-components file only when multiple screens use them. The exact grouping is the builder's call after checking symbol references and imports; avoid creating tiny files for isolated helpers.

## Proof of unchanged behavior

Run the existing unit/UI tests and `./gradlew :app:assembleDebug` before and after. Review the diff as moves/import changes, preserving the pre-existing dirty typography edits. Do not claim pixel parity unless available snapshots are actually run and compared.

## Open to the builder

Choose the smallest cohesive file grouping that reduces navigation through the current monolith. No domain or visual decisions remain open.
