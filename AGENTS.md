# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Overview

`xwindow` is an IntelliJ Platform plugin (Java) that adds a right-anchored Tool Window (titled "工具箱") to the IDE. The window shows a vertical stack of cards: a live clock, current weather, a user/poetry card, and a time-of-day poem. Comments and UI strings are in Chinese.

The plugin is built and test-run against **GoLand 2024.2** (`type = "GO"` in `build.gradle.kts`) but depends only on `com.intellij.modules.platform`, so it loads in any JetBrains IDE within the `233`–`262.*` build range.

## Commands

All commands use the Gradle wrapper from the project root.

- Build the plugin: `./gradlew build`
- Launch a sandbox IDE with the plugin installed (primary dev loop): `./gradlew runIde`
- Produce a distributable zip (in `build/distributions/`): `./gradlew buildPlugin`
- Verify plugin compatibility: `./gradlew verifyPlugin`

There is no test suite in this project. Requires JDK 21 (source/target compatibility is set to Java 21).

The IDE run config `.run/Run IDE with Plugin.run.xml` wraps the `runIde` task.

## Architecture

The code follows a deliberate three-layer separation under `org.plugin.window`:

- **Entry point** — `WindowFactory` implements `ToolWindowFactory` (registered in `src/main/resources/META-INF/plugin.xml`). It instantiates `MainWindow` and wraps `getContent()` in a `Content`.
- **`MainWindow`** — owns the overall `BorderLayout`/`BoxLayout` composition and the **refresh lifecycle**. It holds three Swing `Timer`s (clock every 1s, weather every 30min, poem every 1h) plus a manual refresh button. Long-running work (weather/poem) is dispatched to a single-thread `ExecutorService`; UI updates always return to the EDT.
- **`panel/`** — each card is a self-contained builder returning a `JPanel`. `PanelGenerator.GenerateCardPanel()` is the shared factory for the rounded-border card style; reuse it for new cards. Panels expose a `GenerateDisplayText()` method that `MainWindow` calls to refresh content. `NewsPanel` is an empty stub.
- **`content/`** — formatting/presentation logic that turns raw data into display strings (e.g. `WeatherContent` maps weather descriptions to emoji and formats the temperature/humidity line; `PoetryContent` returns static HTML).
- **`rpc/gaode/`** — external data access. `GaodeRestApi` calls the AMap (高德) weather REST API over `HttpURLConnection`; `Weather` is the Gson-mapped response model (`lives`/`forecasts`).

Data flow for a refreshing card: `MainWindow` Timer → `Panel.GenerateDisplayText()` (off-EDT via executor) → `content/*` formatter → `rpc/*` API → back to EDT via `SwingUtilities.invokeLater`.

### Conventions worth matching

- Public builder/refresh methods use PascalCase (`GenerateWeatherCard`, `GenerateDisplayText`) — unusual for Java but consistent throughout; match it when extending existing panels.
- Always use the JetBrains UI components (`JBPanel`, `JBLabel`, `JBColor`, `JBUI.Borders`) rather than raw Swing, for theme compatibility.
- Any network or blocking call must run off the EDT (via the executor) and marshal results back with `invokeLater`.

### Adding a new card

1. Create a `*Panel` in `panel/` with a `Generate<Name>Card()` builder (reuse `PanelGenerator.GenerateCardPanel()`) and, if it has refreshable content, a `GenerateDisplayText()`.
2. Add it to `MainWindow.initLayout()` with a `Box.createVerticalStrut` spacer.
3. If it needs auto-refresh, register a `Timer` in `MainWindow.OpenAutoRefresh()` and wire it into the manual refresh button's listener.

## Notes

- The AMap API key is not hardcoded: it is stored in the IDE credential store (`PasswordSafe`) via `GaodeApiKeyStore`, and write failures surface a warning in the settings page; it is configured in Settings → 工具箱高德地图. The city code defaults to Beijing Dongcheng (`110101`) and is configurable via `GaodeSettings`.
- `Gson` is used by the `Weather` model but is not declared as a dependency — it resolves from the bundled IntelliJ platform libraries.
