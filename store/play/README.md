# Store assets

Graphics for a Google Play Store listing. They are **not** packaged into the
APK and therefore live outside the Android module.

## App icon

| File | Variant |
|---|---|
| `icon-512.png` | Android green, matches the default launcher icon |
| `icon-512-blau.png` | WordPress blue – white bubble on a blue background, see below |

Generated from the same drawing as the launcher icon
(`android/app/src/main/res/drawable/ic_launcher_foreground.xml`).

### Compliance with the specification

Google Play has different requirements for the icon than Android has for the
launcher icon. Checked against the
[app icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications):

| Requirement | Implementation |
|---|---|
| 512 × 512 px | ✅ |
| 32-bit PNG | ✅ RGBA, 8 bits per channel |
| sRGB color space | ✅ |
| at most 1024 KB | ✅ 11.7 KB |
| full square, no rounded corners | ✅ Play applies a 30 % corner radius itself |
| no drop shadow | ✅ Play generates it itself |
| background color instead of transparency | ✅ solid `#10141B` throughout |
| no text, no badges, no ranking claims | ✅ |

The mark measures 328 × 304 px, i.e. 64 % of the edge length, and thus sits
well inside the 154 px corner radius that Play applies.

It is **optically** centered, not geometrically: the bubble body carries
almost the entire area, while the tail weighs next to nothing. Aligned to the
frame, the mark therefore looked as if it had slipped upward.

## Why the blue variant uses inverted colors

Blue `#3858E9` on the dark background only reaches a contrast of 3.3:1; the
speech bubble's tail almost disappears at small sizes. With a white bubble on
a blue background, it is 5.6:1 in both directions, with the brand color
unchanged.

## Why larger than in the launcher icon

In the launcher icon, the manufacturer's mask limits the usable area to 66 of
108 dp – the mark fills 60.5 dp there. On Play, only the corner radius crops,
so the drawing may take up a larger share. The proportions of the drawing
itself are the same in both cases.

## Regenerating

The files are rendered from SVG sources independent of `docker/`. When the
icon changes, they must be regenerated; the path data is in
`ic_launcher_foreground.xml` and can be copied into an SVG unchanged.
