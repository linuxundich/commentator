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
| at most 1024 KB | ✅ about 10 KB |
| full square, no rounded corners | ✅ Play applies a 30 % corner radius itself |
| no drop shadow | ✅ Play generates it itself |
| background color instead of transparency | ✅ solid `#3DDC84` (green) or `#3858E9` (blue) throughout |
| no text, no badges, no ranking claims | ✅ |

The mark measures 222 × 240 px (scale 4.28 per launcher unit), and thus sits
well inside the 154 px corner radius that Play applies.

It is **optically** centered, not geometrically: the bubble body carries
almost the entire area, while the tail weighs next to nothing. Aligned to the
frame, the mark therefore looked as if it had slipped upward.

## Why both variants use a full-color background

Both variants put a white bubble on a full-color background. The green one
used to be a green bubble on a near-black background (`#11131A`), which
vanished on dark launchers and made the icon look smaller than the
full-color icons of other apps. On the green background the W is dark
(`#11131A`), because a green W on white would be too faint.

For the blue variant there is a second reason: blue `#3858E9` on the dark
background only reaches a contrast of 3.3:1; with a white bubble on blue it
is 5.6:1 in both directions.

## Why larger than in the launcher icon

In the launcher icon, the manufacturer's mask limits the usable area to 66 of
108 dp – the mark fills 60.5 dp there. On Play, only the corner radius crops,
so the drawing may take up a larger share. The proportions of the drawing
itself are the same in both cases.

## Regenerating

The PNGs are rendered from `icon-512.svg` and `icon-512-blau.svg` next to
them. The path data is the same as in `ic_launcher_foreground.xml`; when the
icon changes, update both and render again:

```bash
for f in icon-512 icon-512-blau; do rsvg-convert $f.svg | magick - PNG32:$f.png; done
```
