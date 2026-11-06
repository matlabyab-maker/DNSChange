# DNSChange

Android DNS manager with country/provider lists, editable DNS1/DNS2, local VPN permission flow, and GitHub Actions build.

## Build
Open in Android Studio or run:
`gradle assembleDebug`

GitHub Actions builds the debug APK on every branch push and uploads it as `DNSChange-debug`.

## Important first-build limitations
- The Local VPN implementation in this first source drop is a minimal IPv4/UDP DNS forwarder. It does not tunnel normal application traffic.
- TCP DNS (port 53), IPv6 packet forwarding, DNS-over-TLS, DNS-over-HTTPS, per-app routing, and robust network-change recovery are not implemented yet.
- Country lists are curated starter entries, not a live geolocation database. Anycast resolvers can answer from different countries.
- The "online update" control is a placeholder in this first build; it does not yet fetch remote lists. Local save/restore of DNS1/DNS2 and catalog data is provided.
- Do not treat the VPN status as proof that all DNS traffic is being handled until TCP/IPv6 handling and on-device tests are completed.

## Starter resolver sources
- Alibaba Cloud public DNS: 223.5.5.5 / 223.6.6.6
- CIRA Canadian Shield: 149.112.121.10 / 149.112.122.10 (and protected/family variants)
- FDN France: 80.67.169.12 / 80.67.169.40

## Build fix (2026-11-06)
Fixed Kotlin compilation errors in `MainActivity.kt`: view constructors inside the panel receiver now use the Activity context explicitly, the country-title helper adds its view to the correct panel, and the EditText single-line setting uses `setSingleLine(true)`. The Android SDK workflow and VPN service were not changed for this fix.
