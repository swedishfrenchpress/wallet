# Wallet UI visual review

- Target: PR #292 — *Inline errors: one contract, two native expressions* (cashubtc/wallet)
- Base: `main`
- Before: `42277151fa2e42f68e3f503b0a4bab2ea30df30e` (true merge-base)
- After: `8d1d2ad6d812b71bec4171542738428295a03672` (PR head)

Built from two detached worktrees created by `create_review_session.sh`. The
active checkout (`typography-system`, with local modifications) was never read
into the comparison and was not modified.

## Routing

| Platform | Decision | Reason |
|---|---|---|
| Android | Captured | `direct` — `InlineNotice.kt`, `CashuTextField.kt`, `Color.kt` and 9 call-site screens changed |
| iOS | Captured | `direct` — `ErrorBannerView.swift` (severity + both surfaces), `ScannerWrapperView.swift`, `SendView.swift` and 6 further views changed |

Non-app changes (`docs/product/*.md`) implicate no build.

## Capture environments

| Environment | Runtime | Device and viewport | Appearance | Locale and text |
|---|---|---|---|---|
| `android-api36-phone-light` | Android 16, API 36, build `BE2A.250530.026.D1`, patch 2025-07-05, arm64-v8a | Pixel 8 emulator profile, 1080×2400 px @ 420 dpi (411×914 dp) | light | en-US, font scale 1.0 |
| `android-api36-phone-dark` | *(identical)* | *(identical)* | dark | en-US, font scale 1.0 |
| `ios-26.5-iphone17pro-light` | iOS 26.5 (23F77), Xcode 26.6 (17F113) | iPhone 17 Pro, 1206×2622 px / 402×874 pt @ 3.0× | light | en-US, Dynamic Type Large |
| `ios-26.5-iphone17pro-dark` | *(identical)* | *(identical)* | dark | en-US, Dynamic Type Large |

Runtime policy — two disclosed deviations from "newest stable", both deliberate:

- **Android 17 / API 37 was excluded because it is still beta.** `developer.android.com/about/versions/17` describes Android 17 Beta and QPR1 Beta only; the skill excludes beta images. The newest *stable* image, API 36, was used. Note the repo compiles against `compileSdk = 37` and the leftover `wallet_review_api37` AVD suggests earlier evidence for this PR was shot on the beta image.
- **iOS 26.5 was used although 26.6 is the newest stable release.** The 26.6 simulator runtime is not installed; downloading it was declined in favour of starting immediately. Both revisions deploy to `IPHONEOS_DEPLOYMENT_TARGET = 18.0` and the diff contains no `@available`/`#available` gate, so 26.5 exercises every path in it.

No boundary runtime was added: `minSdk`/`targetSdk`/`compileSdk` and the iOS
deployment target are byte-identical at both revisions.

## Diff-to-screen analysis

- **Android, the shared component.** `InlineNotice` stopped deriving its own fill (`colorScheme.error.copy(alpha = 0.18f)`) and painting full-strength `error` as the text colour. Every severity now pairs a container role with its content role — `errorContainer`/`onErrorContainer`, and the two new `onPendingContainer`/`onReceivedContainer` tokens for the hues that live outside `MaterialColorScheme`. Glyphs moved outlined → filled; body type moved `labelMedium` → `bodyMedium`; padding 10 → 12 dp; icon 14 → 18 dp. `NoticeSeverity.Warning` was renamed `Caution`.
- **Android, the field channel.** `CashuTextField`'s `errorContainerColor` moved from a derived `error@0.12` to the `errorContainer` role, and the previously-forwarded-but-never-passed `supportingText` slot is now used by 7 call sites. Visible consequence: one signal where there were two.
- **iOS, surface collapse.** `InlineNotice`'s `tinted:` parameter is gone, so the boxed rendering that 9 call sites requested no longer exists; `ErrorBannerView` moved from a flat `systemRed@0.18` fill with a `Color(.separator)` hairline to `.regularMaterial` with colour confined to the glyph. `ErrorSeverity` gained `success`.
- **Both scanner overlays stopped copying their component.** iOS `ScannerWrapperView` had been *restyled* into a hand-rolled reproduction of `ErrorBannerView`'s body — same material, radius, glyph font, and combined accessibility element — and now calls the component. Android `ScannerView.kt:337` kept a bare themed `Text` on the unthemed camera surface and now renders `InlineNotice`. This is a second capture round against a new head; see the note below.
- **Two further iOS status sites moved onto the severity tokens**: the `"Expired"` badge in `ReceiveLightningView` (whose countdown had already moved) and the completed/failed heroes in `TransactionDetailView`, which also takes `.green` onto `ErrorSeverity.success.foreground`. Remaining bare `.red` is destructive actions only, which is correct per HIG.
- **Intentional non-changes used as controls.** Android V8/V9/V10 (the error-subtitle row, the icon+text warning row, the centered warning hero) are untouched by this diff and are captured deliberately — they must render identically on both sides, and they do.
- **Not visible in any image.** The restored VoiceOver `"Caution. "` prefix, the new `semantics { error(...) }` on the field, and the `liveRegion` announcement are code changes with no static rendering.

## Inline-error catalog — shared notice contract

Expected change: fills pair with content roles; the field-attached message moves
into the M3 supporting-text slot on Android; iOS gains a fourth severity and
loses its second (boxed) surface.

| Platform | Before | After |
|---|---|---|
| Android, dark | `android/api36/android-before-matrix-api36-411dp-dark.png` | `android/api36/android-after-matrix-api36-411dp-dark.png` |
| Android, light | `android/api36/android-before-matrix-api36-411dp-light.png` | `android/api36/android-after-matrix-api36-411dp-light.png` |
| iOS, dark | `ios/ios26.5/ios-before-matrix-ios26.5-iphone17pro-dark.png` | `ios/ios26.5/ios-after-matrix-ios26.5-iphone17pro-dark.png` |
| iOS, light | `ios/ios26.5/ios-before-matrix-ios26.5-iphone17pro-light.png` | `ios/ios26.5/ios-after-matrix-ios26.5-iphone17pro-light.png` |

Observed: on Android the before column shows the mint-URL error twice — an
`error@0.12` field plus a separate boxed notice — and the after column shows the
field owning it alone. On iOS the before column shows the containerless caption
*and* the tinted box for the same component; the after column shows one
rendering for both, and `success` appears for the first time.

## Inline-error catalog — hand-rolled variants

Expected change: the bypassing variants route through the component or its
tokens; the untouched variants stay put.

| Platform | Before | After |
|---|---|---|
| Android, dark | `android/api36/android-before-variants-api36-411dp-dark.png` | `android/api36/android-after-variants-api36-411dp-dark.png` |
| Android, light | `android/api36/android-before-variants-api36-411dp-light.png` | `android/api36/android-after-variants-api36-411dp-light.png` |
| iOS, dark | `ios/ios26.5/ios-before-variants-ios26.5-iphone17pro-dark.png` | `ios/ios26.5/ios-after-variants-ios26.5-iphone17pro-dark.png` |
| iOS, light | `ios/ios26.5/ios-before-variants-ios26.5-iphone17pro-light.png` | `ios/ios26.5/ios-after-variants-ios26.5-iphone17pro-light.png` |

Observed: Android V7 loses its second red; V8/V9/V10 are pixel-stable. iOS H1
loses its tint box, H2 swaps `Color.red` + unfilled circle for the semantic
token + filled triangle, H4 swaps the solid red slab for material.

**Light-mode legibility finding, iOS H4.** In the before column the scanner
slab is solid `Color.red` with `.primary` text — which in light appearance is
**black on saturated red**. The after column's material treatment removes that
pairing. This is a contrast problem the dark-mode captures alone do not reveal.

Fixture: a debug-only catalog was added *identically to both worktrees* —
`CatalogActivity` (Android, `src/debug`, launched by intent extra) and
`ReviewCatalogView` (iOS, `#if DEBUG`, reached via `SHOW_REVIEW_CATALOG`). Both
render fixed synthetic copy with identical section labels on both sides, so the
only thing that can differ between paired images is the components themselves.
No wallet, mint, seed, balance, invoice, or contact data exists in any capture.

The PR's own `ComponentCatalogView`/`InlineErrorCatalogTest` were *not* used for
the pair: they exist only on the after side and therefore cannot form a matched
comparison.

## Cross-platform parity

| Surface | Android behavior | iOS behavior | Divergence | Introduced or pre-existing |
|---|---|---|---|---|
| Severity vocabulary | 4 tiers: Error, Caution, Info, Success | 4 tiers: error, caution, info, success | None after the change | Resolved — before, iOS had only 3 (no `success`) |
| Severity glyphs | Filled circle for error, filled triangle for caution | Filled triangle for error, filled circle for caution | **Error and caution glyphs are transposed between platforms** | Introduced by this change, deliberately (Material vs HIG convention) |
| In-context container | Always a tonal container (`errorContainer` et al.) | Never a container — caption text only | Container vs containerless | Introduced deliberately; the stated "converge on semantics, diverge on expression" direction |
| Field-attached error | M3 `supportingText`, owned by the field | Caption under the control | Equivalent contract, native expression | Resolved — before, Android double-signalled and iOS did not |
| Scanner overlay | `InlineNotice` tonal container on the camera surface | `ErrorBannerView` on `.regularMaterial` | Different channel *component*, but each is now its own platform's shared one | **Resolved** — before, both were bespoke and shared nothing |
| Floating/async channel | No dedicated floating channel; the scanner uses the in-context component | `ErrorBannerView` on `.regularMaterial` | **Android still has no `Snackbar`-equivalent floating channel** | Pre-existing; PR defers it to phase 4 |
| Detail line | Second line at 78% of the content role | Second line at `.secondary` | Cosmetic only | Pre-existing |

Parity evidence: the matrix and variants captures above are matched
platform-to-platform under the same fixture, copy, appearance, locale, and text
scale, so the two apps can be read side by side.

The glyph transposition is worth an explicit decision. It is intentional per
`inline-error-fixes.md` §2, and each platform is individually conventional, but
a user moving between the two apps sees a triangle mean "error" on one and
"caution" on the other.

## Limitations and noise

- Static screenshots do not prove payment, secure-storage, NFC, or background behaviour.
- VoiceOver/TalkBack announcements, the restored `"Caution. "` prefix, `semantics { error(...) }`, and the polite live region are not observable in any image.
- Catalog surfaces render components directly; they do not prove the real screens' state wiring. The PR's claim that 7 Android call sites moved to `supportingText` is verified by diff reading, not by these images.
- Only one runtime per platform, one device profile, one locale (en-US), and one text scale were captured. No RTL, no large Dynamic Type, no Increase Contrast, no tablet width.
- Android system UI was pinned to demo mode (09:41, fixed battery/signal). Emulator ANR dialogs were suppressed for the session and restored afterwards.
- Every capture was gated on an on-screen content assertion (the section label had to be present in the view hierarchy) after three earlier attempts silently captured the splash window or the launcher. All 16 final images were additionally inspected by eye.
