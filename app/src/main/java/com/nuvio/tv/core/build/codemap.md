# app/src/main/java/com/nuvio/tv/core/build/

## Responsibility

Defines the closed set of trailer playback destinations: in-app playback or external playback.

## Design

`TrailerPlaybackMode` is a two-value enum, `IN_APP` and `EXTERNAL`. It expresses a playback policy as a named type instead of a boolean, so callers can exhaustively branch on the selected destination.

## Flow

The package contains only the enum. No selection, persistence, or network flow is implemented here; a caller must choose a mode and route the trailer accordingly.

## Integration

There are no current production Kotlin call sites for `TrailerPlaybackMode`. Trailer networking and playback code live elsewhere, so this package currently acts as an isolated shared contract.
