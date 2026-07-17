# Lume

Lume is a personal Android TV application for browsing movies and series through TMDB and playing authorized VOD sources supplied by a build-time Xtream account.

## Product Model

- TMDB provides discovery, search, synopsis, artwork, recommendations, seasons, and episodes.
- Xtream is consulted only when playback is requested.
- Titles without a safe provider match remain visible and display `Em breve` when played.
- Library, playlists, and watch progress remain on the device.
- Live TV, EPG, provider login, Stremio addons, plugins, and debrid services are not part of the Lume experience.

## Local Configuration

Create `local.properties` from `local.example.properties` and provide:

```properties
TMDB_API_KEY=your_tmdb_v3_api_key
XTREAM_BASE_URL=http://your-authorized-provider.example
XTREAM_USERNAME=your_username
XTREAM_PASSWORD=your_password
```

These values are compiled into the APK. They are excluded from Git, but they can be extracted from an APK and an HTTP Xtream endpoint transmits credentials without TLS.

## Build

```bash
./gradlew :app:testFullDebugUnitTest
./gradlew :app:assembleFullDebug
```

The universal APK is generated under `app/build/outputs/apk/full/debug/`.

## License

Lume is based on NuvioTV and remains licensed under GNU GPL v3. See `LICENSE` for the complete terms and retain upstream copyright notices.

This product uses the TMDB API but is not endorsed or certified by TMDB. Lume does not host or distribute media and is intended only for content the user is authorized to access.
