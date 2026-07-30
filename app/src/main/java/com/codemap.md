# app/src/main/java/com/

## Responsibility

The Java package namespace beneath which Lume's application code is published. Its production child
namespace is `nuvio`, which keeps the application implementation under a stable package name shared
by the common source set and flavor overlays.

## Design

This level is a namespace boundary rather than a service layer. Package names are kept identical in
the full and Play Store source sets so Gradle can replace implementations without changing imports,
Hilt contracts, navigation code, or domain-facing APIs.

## Flow

All application control enters the `com.nuvio.tv` subtree. Startup and activity lifecycle code flow
into the core, data, domain, and UI packages; provider responses and local state return through those
packages as domain models and observable flows.

## Integration

The namespace is compiled with Android framework code, Hilt, Compose, Media3, and the selected flavor
overlay. The next package boundary, `com.nuvio`, owns the Lume namespace, while `com.nuvio.tv` owns
the application and its child feature layers.
