# Patched H3 Android artifact

`h3-android-4.4.0-kartoffel.1.aar` is a temporary rebuild of
`com.uber:h3-android:4.4.0` for Kartoffel.

The published Android native libraries omit `libm.so` from their ELF dependencies and fail to
load on Android. This artifact rebuilds both supported ABIs with the fix from
[uber/h3-java#211](https://github.com/uber/h3-java/pull/211), commit `e4cb720` in
[`geirsagberg/h3-java`](https://github.com/geirsagberg/h3-java/commit/e4cb720).

- H3 Java version: 4.4.0
- H3 core version: 4.4.1
- ABIs: `arm64-v8a`, `armeabi-v7a`
- SHA-256: `3c3c1bda30b6bdc363e7c1c7667311c28e875f027c8db4399eaf34d16d75a6dd`
- License: [Apache License 2.0](https://github.com/uber/h3-java/blob/v4.4.0/LICENSE)

Replace this file with the official Maven Central dependency once an upstream release contains
the fix.
