---
id: mv-8wl5
status: open
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: task
priority: 3
assignee: Alexander Q
tags: [ui, preview, m5]
---
# Preview: follow the app theme, not the system dark mode

ui/card/PreviewPage.kt:100 sets night = isSystemInDarkTheme(). Once milestone 5 adds the app theme setting, the preview must follow the app's theme instead. Fix in M5 with the theme setting.

