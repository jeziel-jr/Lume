# app/src/full/java/com/lagradost/cloudstream3/network/

## Responsibility
`CloudflareKiller` is an OkHttp interceptor that retries Cloudflare 403 or 503 responses through a WebView and reuses the resulting cookies.

## Patterns
- Per-host mutable cookie cache with WebView `cf_clearance` discovery.
- Header composition always includes the app user agent, request headers, and parsed cookies.
- Interception runs through `runBlocking` so the synchronous OkHttp chain can call suspendable WebView resolution.

## Flow
A request without saved host cookies proceeds normally. A Cloudflare error closes the response, checks existing WebView cookies, otherwise opens `WebViewResolver`, stores solved cookies, and retries through `app.baseClient`. Saved cookies bypass the challenge on later requests.

## Integration
The interceptor uses the shared CloudStream `app.baseClient`, `USER_AGENT`, `WebViewResolver`, and Android `CookieManager`. Plugin extensions reach it through their normal HTTP path.
