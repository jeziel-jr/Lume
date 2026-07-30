# baselineprofile/src/main/java/com/nuvio/

## Responsibility

Namespace container for Lume's baseline profile test package. No classes or
module boundaries are defined directly here.

## Package role

The nested `tv` directory continues the `com.nuvio.tv` namespace used by the
app and by the app's baseline profile method filter.

## Flow and integration

Source beneath this namespace is compiled by `:baselineprofile`, installed as
instrumentation code, and executed against the app selected by
`targetProjectPath = ":app"`.
