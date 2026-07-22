# Design System — LizardLens Android App

## Brand

**Name:** LizardLens
**Tagline:** Offline lizard detection for field researchers
**Package:** `com.lizardlens`

---

## Colour Palette

Field use means bright sunlight, green/brown backgrounds, and variable lighting. The palette prioritises contrast and readability outdoors.

### Primary

| Token | Hex | Usage |
|---|---|---|
| `Primary` | `#1B5E20` | App bar, FAB, active nav icon |
| `OnPrimary` | `#FFFFFF` | Text on primary |
| `PrimaryContainer` | `#A5D6A7` | Chips, badges, selected states |
| `OnPrimaryContainer` | `#002204` | Text on primary container |

### Secondary

| Token | Hex | Usage |
|---|---|---|
| `Secondary` | `#4E6E50` | Secondary buttons, filters |
| `OnSecondary` | `#FFFFFF` | Text on secondary |
| `SecondaryContainer` | `#D0E8D1` | Subtle backgrounds |
| `OnSecondaryContainer` | `#0C1F0E` | Text on secondary container |

### Surface / Background

| Token | Hex | Usage |
|---|---|---|
| `Surface` | `#F8FBF8` | Screen background (light) |
| `OnSurface` | `#1A1C1A` | Body text |
| `SurfaceVariant` | `#DDE5DD` | Card backgrounds, dividers |
| `Outline` | `#717971` | Borders, inactive icons |

### Detection Overlay

| Token | Hex | Usage |
|---|---|---|
| `DetectionBox` | `#00FF88` | Bounding box stroke |
| `DetectionLabel` | `#00FF88` | Confidence text, label badge |
| `DetectionFill` | `#3300FF88` | Semi-transparent box fill |
| `DetectionBackground` | `#99000000` | Label background pill |

### Status

| Token | Hex | Usage |
|---|---|---|
| `Success` | `#4CAF50` | Detection confirmed, GPU active |
| `Warning` | `#FF9800` | Thermal moderate, low confidence |
| `Error` | `#F44336` | Thermal critical, permission denied |
| `Info` | `#2196F3` | FPS counter, CPU indicator |

### Thermal Status

| Level | Indicator Colour | Behaviour |
|---|---|---|
| Normal | None (hidden) | Full 10 FPS inference |
| Moderate | `#FF9800` amber chip | Show "Device warming" banner |
| Severe | `#F44336` red chip | Drop to ~3 FPS, show "Throttled" banner |
| Critical | `#F44336` pulsing red | Stop camera, show "Cooling down" screen |

---

## Typography

Material 3 type scale. Use system default (Roboto) — no custom fonts to keep APK small.

| Role | Size | Weight | Usage |
|---|---|---|---|
| Headline Medium | 28sp | Bold | Screen titles |
| Title Large | 22sp | SemiBold | Section headers |
| Title Medium | 16sp | Medium | Card titles, nav labels |
| Body Large | 16sp | Regular | Body text, descriptions |
| Body Medium | 14sp | Regular | List items, secondary text |
| Body Small | 12sp | Regular | Timestamps, metadata |
| Label Large | 14sp | Medium | Buttons, chips |
| Label Medium | 12sp | Medium | Badges, tags |
| Label Small | 10sp | Regular | FPS counter, tiny indicators |

---

## Spacing & Layout

| Token | Value | Usage |
|---|---|---|
| `xs` | 4dp | Icon padding, tight gaps |
| `sm` | 8dp | List item internal padding |
| `md` | 12dp | Card padding, section gaps |
| `lg` | 16dp | Screen horizontal padding |
| `xl` | 24dp | Section spacing |
| `xxl` | 32dp | Screen top/bottom padding |

### Screen Structure

The camera is the primary experience. It fills the entire screen. Everything else is secondary.

```
┌──────────────────────────────┐
│  LizardLens   🖼  🎬  📋  ⚙️│  ← top bar: title + secondary actions
├──────────────────────────────┤
│                              │
│                              │
│      Camera Preview          │
│      (full bleed)            │
│                              │
│                              │
│                              │
├──────────────────────────────┤
│         ┌──────────┐         │
│         │  🦎 DETECT│         │  ← large centre button
│         └──────────┘         │
└──────────────────────────────┘
```

**Rationale:** Field researchers open the app to detect lizards. The camera should be one tap away — not buried behind a tab. Image, video, and history are secondary actions accessed from the top bar.

---

## Components

### Centre Detect Button

The primary action. Large, tappable, impossible to miss.

- **Position:** Bottom centre of screen, above the navigation bar
- **Size:** 72×72dp circular FAB
- **Background:** `Primary` (`#1B5E20`)
- **Icon:** `pest_control` (lizard icon) or `videocam`, 32dp, white
- **Label:** `"DETECT"` below icon, 12sp Label Medium, white
- **State — idle:** Solid `Primary` background
- **State — detecting:** Pulsing ring animation (`#00FF88`), icon spins slowly
- **State — paused (thermal):** Greyed out, `#9E9E9E`
- **Elevation:** 6dp (raised above camera preview)

### Top Action Bar

Replaces the standard Material 3 top bar. Minimal, transparent over camera.

```
┌──────────────────────────────┐
│  LizardLens   🖼  🎬  📋  ⚙️│
└──────────────────────────────┘
```

| Icon | Action | Opens |
|---|---|---|
| `photo` | Pick image | Image detection screen (overlay/modal) |
| `movie` | Pick video | Video detection screen (overlay/modal) |
| `history` | View history | History screen (overlay/modal) |
| `settings` | App settings | Settings screen |

- **Background:** `#99000000` (60% black) — transparent over camera
- **Icons:** 24dp, white
- **Title:** `"LizardLens"`, 16sp Title Medium, white, left-aligned

### Secondary Screens (Image / Video / History)

When tapped from the top bar, these open as **full-screen overlays** that slide up from the bottom, covering the camera. A back arrow or swipe-down dismisses them and returns to the live camera.

This keeps the camera as the "home" state — you always return to it.

### Detection Bounding Box

Drawn as an overlay on camera preview / image / video frame.

```
┌──────────────────────────────┐
│  ┌─────────────────────┐     │
│  │                     │     │
│  │    Lizard           │     │
│  │    0.87             │     │
│  │                     │     │
│  └─────────────────────┘     │
│                              │
└──────────────────────────────┘
```

- **Stroke:** 3dp `#00FF88`
- **Fill:** `#3300FF88` (20% opacity)
- **Label pill:** `#99000000` background, `#00FF88` text, 10sp Label Medium
- **Label position:** Top-left of bounding box, shifted up 4dp
- **Label content:** `"Lizard {confidence}"` e.g. `"Lizard 0.87"`

### Confidence Badge

Shown in camera viewfinder and history list.

- **Shape:** Rounded rectangle, 12dp corner radius
- **Background:** Confidence colour (see scale below)
- **Text:** 10sp white, bold
- **Size:** 40×20dp

| Confidence | Colour | Meaning |
|---|---|---|
| ≥ 0.70 | `#4CAF50` green | High — reliable |
| 0.45 – 0.69 | `#FF9800` amber | Medium — review |
| < 0.45 | Filtered out | Below threshold, not shown |

### FPS Counter

Small overlay in top-right of camera viewfinder.

- **Text:** `"12 FPS"` in 10sp Label Small
- **Colour:** `#2196F3` on `#99000000` background
- **Position:** 8dp from top-right corner
- **Visibility:** Toggleable via settings

### Accelerator Indicator

Small chip below FPS counter showing current delegate.

- **CPU:** `"CPU"` chip, `#2196F3` blue
- **GPU:** `"GPU"` chip, `#4CAF50` green
- **Shape:** Rounded pill, 8dp corner radius
- **Size:** 32×16dp

### Thermal Warning Banner

Appears at top of camera viewfinder when device is warming.

- **Position:** Below top app bar, full width
- **Height:** 40dp
- **Background:** Status colour (amber/red)
- **Text:** 12sp white, centred
- **Content:** `"Device warming — reduce usage"` or `"Throttled — 3 FPS"`
- **Dismissable:** No — auto-hides when status improves

---

## Screens

### 1. Camera Screen (Home — default)

The app opens directly to this screen. Full-bleed camera with detection overlay.

```
┌──────────────────────────────┐
│  LizardLens   🖼  🎬  📋  ⚙️│
│──────────────────────────────│
│  ⚠️ Device warming           │  ← thermal banner (conditional)
│┌────────────────────────────┐│
││                            ││
││                            ││
││      Camera Preview        ││
││                            ││
││   ┌──────────────┐         ││
││   │ Lizard       │         ││  ← bounding boxes
││   │ 0.87         │         ││
││   └──────────────┘         ││
││                            ││
││                    12 GPU  ││  ← FPS + accelerator
│└────────────────────────────┘│
│         ┌──────────┐         │
│         │  🦎 DETECT│         │  ← centre button (pulsing when active)
│         └──────────┘         │
└──────────────────────────────┘
```

**Interactions:**
- Tap DETECT → starts/stops live detection (toggle)
- Tap camera switch icon (bottom-right of preview) → toggle front/rear camera
- Double-tap viewfinder → freeze frame (inspect detection)
- Tap top bar icons → open Image / Video / History as slide-up overlays

### 2. Image Detection (overlay)

Slides up from bottom. Dismiss with back arrow or swipe down — returns to camera.

```
┌──────────────────────────────┐
│  ← Image Detection           │
├──────────────────────────────┤
│                              │
│  ┌──────────────────────────┐│
│  │                          ││
│  │   Selected Image         ││
│  │   with bounding boxes    ││
│  │                          ││
│  │   ┌────────────┐         ││
│  │   │ Lizard     │         ││
│  │   │ 0.92       │         ││
│  │   └────────────┘         ││
│  │                          ││
│  └──────────────────────────┘│
│                              │
│  2 detections found          │
│                              │
│  ┌──────────────────────────┐│
│  │       Pick Photo         ││
│  └──────────────────────────┘│
└──────────────────────────────┘
```

**Interactions:**
- Tap "Pick Photo" → system photo picker
- Pinch-to-zoom on result image
- Long-press detection → show details (confidence, bbox coords)
- Back arrow / swipe down → return to camera

### 3. Video Detection (overlay)

Slides up from bottom. Dismiss with back arrow or swipe down — returns to camera.

```
┌──────────────────────────────┐
│  ← Video Detection           │
├──────────────────────────────┤
│  ┌──────────────────────────┐│
│  │  Progress: 142/380 frames││
│  │  ████████████░░░░░ 37%   ││
│  └──────────────────────────┘│
│                              │
│  ┌──────────────────────────┐│
│  │  Frame 0:02 — 1 detected ││
│  │  Frame 0:05 — 2 detected ││
│  │  Frame 0:11 — 1 detected ││
│  │  ...                     ││
│  └──────────────────────────┘│
│                              │
│  ┌──────────────────────────┐│
│  │       Pick Video         ││
│  └──────────────────────────┘│
└──────────────────────────────┘
```

**Interactions:**
- Tap "Pick Video" → system video picker
- Tap frame entry → open frame viewer with bounding boxes
- Processing runs in foreground with notification
- Back arrow / swipe down → return to camera

### 4. History (overlay)

Slides up from bottom. Dismiss with back arrow or swipe down — returns to camera.

```
┌──────────────────────────────┐
│  ← History            🗑️    │
├──────────────────────────────┤
│  [All] [Camera] [Image] [Vid]│  ← filter chips
├──────────────────────────────┤
│ ┌────┬──────────────────────┐│
│ │ 🖼 │ Lizard — 0.87        ││
│ │    │ 2 min ago · Image    ││
│ ├────┼──────────────────────┤│
│ │ 📷 │ Lizard — 0.92        ││
│ │    │ 15 min ago · Camera  ││
│ ├────┼──────────────────────┤│
│ │ 🎬 │ Lizard — 0.71        ││
│ │    │ 1 hr ago · Video     ││
│ └────┴──────────────────────┘│
│                              │
│  (pull to refresh)           │
└──────────────────────────────┘
```

**Interactions:**
- Tap entry → full-screen view of detected image with bounding boxes
- Swipe left → delete entry (with undo snackbar)
- Tap "Clear all" → confirmation dialog → delete all
- Tap filter chip → filter by source type
- Back arrow / swipe down → return to camera

### 5. Settings (overlay)

Accessible from top bar gear icon. Slides up from bottom.

```
┌──────────────────────────────┐
│  ← Settings                  │
├──────────────────────────────┤
│                              │
│  Detection                   │
│  ┌──────────────────────────┐│
│  │ Confidence Threshold     ││
│  │ ●────────────○─── 45%   ││
│  └──────────────────────────┘│
│                              │
│  Acceleration                │
│  ┌──────────────────────────┐│
│  │ ( ) Auto                 ││
│  │ (•) CPU only             ││
│  │ ( ) GPU only             ││
│  └──────────────────────────┘│
│                              │
│  Comfort                     │
│  ┌──────────────────────────┐│
│  │ Thermal warnings    [ON] ││
│  └──────────────────────────┘│
│                              │
│  About                       │
│  ┌──────────────────────────┐│
│  │ Model: yolov8n_lizard    ││
│  │ Input: 416×416 FP16      ││
│  │ Version: 1.0             ││
│  └──────────────────────────┘│
└──────────────────────────────┘
```

---

## Accessibility

- All interactive elements have minimum 48×48dp touch target
- Detection bounding boxes use colour + label (not colour alone) for colour-blind users
- Thermal warnings use icon + text (not colour alone)
- Minimum contrast ratio 4.5:1 for all text (WCAG AA)
- Content descriptions on all icons (`contentDescription` in Compose)

---

## Dark Mode

Not in v1 scope. Material 3 defaults to light theme. Can be added later by defining dark colour tokens — the detection overlay colours (`#00FF88`) work well on both light and dark backgrounds.

---

## Responsive Layout

Phone-first. No tablet or foldable optimisation in v1.

- **Portrait:** Default orientation. All screens designed for portrait.
- **Landscape:** Camera screen supports landscape via `BoxWithConstraints`. Other screens remain portrait-locked.

---

## Animation

Minimal — field tool, not a media app.

- **Bounding box appear:** Fade in 100ms
- **Bounding box disappear:** Fade out 150ms
- **Thermal banner:** Slide down 200ms
- **Tab switching:** Material 3 default crossfade
- **List item delete:** Slide out left 200ms + undo snackbar (3s)
