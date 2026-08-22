# Hill Bros Video

An Android video editor built with Jetpack Compose and Media3. Import clips,
arrange them on a timeline, trim, grade, add text and music, and export a
single MP4 to your gallery.

## Getting the app on your phone

Every push builds an APK in CI and attaches it to the rolling
[`app-latest` release](../../releases/tag/app-latest).

1. Open that release on your phone and download `hillbros-video-*.apk`.
2. Android will ask to allow installs from your browser — allow it.
3. Open the downloaded file to install.

The APK is signed with the standard Android debug key, so it installs
side-by-side without a Play Store account. Replace `signingConfig` in
`app/build.gradle.kts` with a real keystore before distributing it publicly.

## What it does

| Area | Capability |
| --- | --- |
| Import | Multi-select via the system photo picker (no storage permission needed) |
| Timeline | Reorder, duplicate and delete clips; running total duration |
| Trim | Non-destructive in/out points per clip, to a tenth of a second |
| Colour | Brightness, contrast, saturation, plus Mono/Sepia/Vivid/Cool/Warm/Invert |
| Transform | 90° rotation, 0.5x–2x speed |
| Text | Overlay text per clip with size, colour and top/centre/bottom placement |
| Audio | Per-clip volume and mute; one background music track mixed under the timeline |
| Export | 480p/720p/1080p/original, H.264 + AAC, saved to `Movies/HillBros` |

Projects are metadata only — trimming and grading never modify your source
files, and everything is stored in `projects.json` in app storage.

## Architecture

```
data/     Models (Clip, VideoProject) + JSON-backed ProjectRepository
media/    ClipEffects  — maps edit settings to a Media3 effect chain
          VideoExporter — Transformer pipeline, progress, gallery save
          MediaUtils   — duration/name probing, URI permissions, formatting
ui/       EditorViewModel (preview player + edits + export), ProjectsViewModel
          screens/ ProjectsScreen, EditorScreen
          components/ PlayerSurface, Timeline, EditPanels
```

`ClipEffects` is deliberately shared between the ExoPlayer preview and the
Transformer export, so the preview reflects what actually gets rendered.
Speed is the one exception: preview uses playback parameters while export uses
`SpeedChangeEffect`, so it is filtered out of the preview effect chain to avoid
being applied twice.

## Building locally

Requires JDK 17 and the Android SDK (compileSdk 35, minSdk 26).

```bash
./gradlew assembleDebug
```

## Known limits

- Background music is trimmed to the timeline length; it does not loop to fill
  a longer video.
- Preview applies effects per clip as playback moves between them, so a
  transition between two differently-graded clips updates at the boundary
  rather than cross-fading.
- Export runs in the foreground — keep the app open while it renders.
