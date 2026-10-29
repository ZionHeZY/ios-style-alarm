# iOS-Style Alarm for Android

[简体中文](README.md) | **English**

An iOS-inspired alarm app built with Jetpack Compose and Android 13+ AGSL (`RuntimeShader`), with a reusable `:glass` module for liquid glass controls.

The public version uses Android system fonts and generic geometric alarm icons. It does not bundle Apple fonts or SF Symbols assets. The three screenshots, slider close-up, and full-screen ringing animation were captured from the public build. The list scrolling, progressive blur, and editor animations show an earlier local research build: fonts and small icons may differ, but the glass rendering and gesture implementations are the same. This project is not affiliated with Apple.

<p align="center">
  <img src="docs/assets/screen-list.png" width="31%" alt="Alarm list" />
  <img src="docs/assets/screen-editor.png" width="31%" alt="Add alarm sheet" />
  <img src="docs/assets/screen-ringing.png" width="31%" alt="Ringing screen" />
</p>

---

## Interaction Demos

### 1. Liquid Glass Slider and Full-Screen Alarm

The `:glass` module provides the controls at the bottom of the ringing screen. Dragging **slide to stop** moves a circular glass lens over the track and text. The shader combines Snell-law refraction, RGB chromatic dispersion, magnification, and Fresnel-inspired edge highlights. A moving orange reflection follows the geometry of the **Snooze** button above it.

<p align="center">
  <img src="docs/assets/liquid-glass-slider-closeup.gif" width="48%" alt="Liquid glass slider close-up" />
  <img src="docs/assets/slider-refraction-detail.png" width="48%" alt="Text refraction and orange reflection detail" />
</p>

### 2. Progressive Blur, GlassToggle, and GlassButton

As the alarm list scrolls behind the toolbar, `progressiveTopBlur` applies a two-pass, separable Gaussian blur. Its standard deviation, $\sigma$, falls smoothly from `12dp` at the top to `0` at a point `24dp` below the toolbar. The **Edit** and **+** buttons use `GlassBackdrop` to refract the live list beneath them. Pressing or horizontally dragging `GlassToggle` expands its thumb into a transparent capsule lens at `1.5x` scale.

<p align="center">
  <img src="docs/assets/progressive-blur-closeup.gif" width="48%" alt="Progressive toolbar blur close-up" />
  <img src="docs/assets/list-blur-delete.gif" width="32%" alt="List scrolling and swipe-to-delete" />
</p>

### 3. Editor Sheet and Cylindrical Time Picker

The add/edit sheet uses spring-driven entrance and dismissal animations, with a downward drag gesture that can dismiss the sheet or spring back. The background list scales to `0.95x` and gains rounded corners. Nested pages—Repeat, Label, Sound, and Snooze Duration—use parallax transitions and swipe-back navigation. The time picker uses cylindrical perspective and snapping to individual values.

<p align="center">
  <img src="docs/assets/editor-sheet.gif" width="36%" alt="Editor sheet, time picker, and nested pages" />
  <img src="docs/assets/ringing-slider.gif" width="36%" alt="Full-screen alarm and slide-to-stop interaction" />
</p>

---

## Architecture

The project uses two Gradle modules and unidirectional state flow (MVI: Model–View–Intent). Alarm state, persistence, and scheduling live in `:app`; the GPU controls live in `:glass` without depending on alarm business logic.

```text
ios-style-alarm/
├── app/
│   └── src/main/
│       ├── java/tech/hezy/alarm/
│       │   ├── domain/                 # Alarm model and next-trigger calculation
│       │   ├── data/                   # JSON storage, AlarmManager, and synthesized PCM tones
│       │   ├── mvi/                    # MviViewModel base with StateFlow and reduce
│       │   ├── ui/                     # Alarm list, toolbar blur, editor, and navigation
│       │   └── ring/                   # Foreground service, lock-screen activity, and snooze
│       └── res/raw/
│           └── progressive_top_blur.agsl
└── glass/
    └── src/main/
        ├── java/tech/hezy/glassalarmslider/
        │   ├── GlassAlarmControls.kt   # Snooze/stop controls and gesture state machine
        │   ├── GlassAlarmRenderer.kt   # Slider rendering, shimmer, and uniform caching
        │   ├── GlassSceneCapture.kt    # RenderNode capture, downsampling, and blur atlas
        │   ├── GlassControlSurface.kt  # Shared capsule surface for buttons and toggles
        │   ├── GlassBackdrop.kt        # Background display-list recording and replay
        │   ├── GlassButton.kt          # Glass buttons for toolbars and navigation
        │   └── GlassToggle.kt          # Solid resting thumb; refractive lens during interaction
        └── res/raw/
            ├── alarm_glass.agsl        # Circular alarm-slider optics
            └── glass_control.agsl      # Capsule button/toggle optics
```

---

## Implementation

### 1. Liquid Glass Shaders

`alarm_glass.agsl` and `glass_control.agsl` derive lens normals analytically from signed distance fields (SDFs). They do not require a separate normal-map texture or hardware derivative instructions.

- **Geometry and normals:** Circular and capsule geometry provide the distance to the lens surface. A power-law edge profile determines the normal's height component and tangential direction for antialiasing and refraction.
- **Refraction and dispersion:** The shader computes a Snell-law-based displacement, then samples the red, green, and blue channels at slightly different positions to produce chromatic dispersion around the lens edge.
- **Sharp/blurred capture atlas:** When frosting or downsampling is enabled, `GlassSceneCapture` records the source scene in a hardware `RenderNode` and replays it into sharp and pre-blurred capture layers. The blurred layer uses `RenderEffect.createBlurEffect`. Both layers form an atlas that the shader samples for refraction and frosting, without CPU bitmap readback.
- **Area-light reflection:** The slider's edge response depends on the lens position and the orange Snooze button's capsule geometry. This produces an orange reflection that moves with the lens rather than a fixed decorative arc.

### 2. Progressive Toolbar Blur

`ProgressiveTopBlur.kt` uses two separate `CompositingStrategy.Offscreen` layers: a horizontal pass with `axis = (1, 0)` and a vertical pass with `axis = (0, 1)`. Each pass uses a 33-tap one-dimensional Gaussian kernel. Materializing the passes separately avoids nested shader sampling: $33 + 33 = 66$ samples instead of $33 \times 33 = 1089$ in a naively nested implementation.

The blur strength varies with vertical position:

$$\sigma(y) = \sigma_{\max} \cdot \left(1 - \text{smoothstep}(0, y_{\text{fadeEnd}}, y)\right)$$

When $\sigma(y) < 0.05$, the shader returns the original pixel and skips the convolution loop. Offscreen layers and compositing still have a cost; this is not a zero-overhead effect.

### 3. Alarm Scheduling and Synthesized Audio

- **Scheduling:** `AlarmScheduler` uses `AlarmManager.setAlarmClock` for system-managed exact alarms. It supports one-shot alarms, weekly repetition, snooze durations of 1–15 minutes, and rescheduling after reboot or time-zone changes through `BootReceiver`.
- **Audio:** Five tones—Rise, Pulse, Glass, Harbor, and Clarity—are synthesized into two-second PCM buffers at runtime and looped using `AudioTrack`. No prerecorded audio assets are required.

---

## Build and Run

Requirements: **Android Studio Ladybug or later**, **JDK 17**, and an **Android 13 (API 33) or later** device or emulator.

Install **Android SDK Platform 35**. The first build requires network access to download Gradle and Maven dependencies. No NDK, Apple fonts, or private signing files are needed.

```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Install on a connected device or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## License and Acknowledgments

Released under the [MIT License](LICENSE).

The toggle's interaction dimensions and state animations reference `LiquidToggle` from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass). This project does not include its Backdrop library. Android, Jetpack Compose, and other dependencies remain subject to their respective licenses.
