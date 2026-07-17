# Lume Frame 24 identity

## Concept

The identity uses a custom `LUME` wordmark and a 24-degree cut through the `L`, referencing cinema's 24 frames per second. The narrow yellow slit is the system's light device.

## Master assets

- `lume-banner-master.png`: full-bleed dark 16:9 launcher banner with the complete wordmark.
- `lume-icon-master.png`: square launcher icon using the isolated `L`.
- `lume-wordmark-transparent.png`: tightly cropped transparent wordmark for in-app use.
- `lume-fire-tv-appstore-icon-1280x720.png`: opaque submission asset for the Fire TV App Icon field in Amazon Developer Console.
- `fire-tv-sideload-validation.png`: device evidence with the focused Lume card using the embedded square icon.

## Production notes

- Generated with the built-in image generation workflow from the approved Frame 24 direction.
- Banner background is near-black with a restrained radial lift, faint warm flare, edge falloff, and fine cinematic grain.
- The transparent wordmark was generated against a flat chroma key, removed locally, trimmed, and validated before export.
- Square launcher exports use the legacy-compatible 48, 72, 96, 144, and 192 px mipmap sizes. The Android TV banner is 320 x 180 px with the app name embedded in the artwork.
- The official Fire TV launcher obtains its rectangular 1280 x 720 App Icon from Amazon Appstore metadata. Sideloaded APKs not associated with an Amazon listing fall back to the embedded square icon; this was verified on the target `AFTMM` Fire TV.

## Core palette

- Graphite: `#080808`
- Warm ivory: `#F5F1E8`
- Light slit: `#FFD02F`
