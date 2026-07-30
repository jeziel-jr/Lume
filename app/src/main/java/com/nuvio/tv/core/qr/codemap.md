# app/src/main/java/com/nuvio/tv/core/qr/

## Responsibility

Generate the TV setup QR bitmap used to transfer a local setup URL to a phone.

## Design and patterns

`QrCodeGenerator` is a stateless object around ZXing `QRCodeWriter`. It accepts arbitrary content,
size, and non-negative margin, then renders an ARGB bitmap with a white rounded background and
rounded black modules. A second clipped bitmap applies the rounded outer boundary.

## Flow

The caller supplies the URL, ZXing encodes it into a `BitMatrix`, and the generator converts the
matrix to a bitmap for Compose or Android view rendering. It performs no networking, persistence,
or QR payload interpretation.

## Integration

The setup UI combines the generated bitmap with the URL served by `XtreamSetupServer`; the QR
fragment carries that server's per-session key. ZXing and Android graphics are the only direct
infrastructure dependencies.
