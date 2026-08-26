# FA LED Preset Preview Assets

## Ownership

`drawable-nodpi/ipixel_fa_preset_01.gif` through `ipixel_fa_preset_20.gif` are UI previews for the
20 firmware presets exposed by `IpixelFaPreset`. They do not contain protocol payloads. Runtime BLE
commands continue to send only the firmware `presetId` in the range `1..20`.

Every preview is `96x16`, matching the physical FA panel's 6:1 pixel layout. The catalog test keeps
the enum IDs, resource mapping, file type, dimensions, and one-to-one resource ownership aligned.

## Format Decision

The GIF format is explicitly retained for this asset set. A lossless Animated WebP conversion was
verified frame-by-frame and duration-by-duration, but increased the combined resource size from
846,367 bytes to 956,734 bytes, an increase of 13%. Lossy WebP is not appropriate because these are
small pixel graphics whose hard edges are part of the product preview.

`GifUsage` is therefore ignored only for the exact `ipixel_fa_preset_01..20.gif` naming pattern in
`app/lint.xml`. New GIF resources remain visible to Lint.

## Runtime Loading

`IpixelFaPresetPreview` decodes the packaged drawable with platform `ImageDecoder`, displays it at
the native 6:1 aspect ratio, repeats the `AnimatedImageDrawable` while visible, and stops it when the
Compose node is disposed. The application minimum API level is 29, so this path does not require a
compatibility decoder.
