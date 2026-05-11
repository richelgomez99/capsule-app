# Capture Source Identity Plan

## Problem

Two captures can point at the same real-world source, such as YouTube, while Orbit renders different glyphs because it currently mixes three concepts:

- Foreground origin: the app active when the bubble was tapped, such as YouTube, Brave, Chrome, Messages.
- Content provider: the source represented by the captured content, such as a `youtube.com` or `youtu.be` URL copied from any app.
- Category: a durable grouping bucket, such as `VIDEO`, `BROWSER`, `SOCIAL`, or `READING`.

The category is useful for grouping and model context. It should not be the primary source of brand glyph truth.

## Display Rule

Orbit should derive source identity in this order:

1. Strong content provider: if the captured text contains a recognized provider URL, use that provider for the primary glyph. Example: any `youtube.com`, `m.youtube.com`, `youtu.be`, Shorts, or `youtube-nocookie.com` URL renders the YouTube glyph even when copied from Brave or Messages.
2. Foreground app: if there is no recognized content provider, use the foreground app label/package captured before Orbit takes focus.
3. Category fallback: if the app is unknown, use a generic category glyph. `VIDEO` can map to a video glyph, but should not pretend every video category item is YouTube unless the app/provider says so.
4. Unknown fallback: if none of the above is available, render the generic URL/share glyph and plain copy like `from an app`.

## Copy Rule

When provider and foreground app differ, Orbit should preserve both facts:

- Primary glyph/title: content provider, e.g. YouTube.
- Secondary source line: origin app, e.g. `YouTube via Brave` or `YouTube copied from Messages`.

This keeps the visual source stable while still explaining how the capture arrived.

## Implementation Shape

Add a shared source resolver instead of duplicating string checks in Compose screens:

```kotlin
data class CaptureSourceIdentity(
    val provider: SourceProvider?,
    val originAppLabel: String?,
    val appCategory: AppCategory,
    val glyphKind: SourceGlyphKind,
    val displayLabel: String,
    val secondaryLabel: String?,
    val confidence: Confidence,
)
```

The resolver should accept captured text plus `StateSnapshotParcel`, normalize known URL hosts, and return one identity object for Capture, Diary, Detail, and Cluster surfaces.

## Near-Term Patch

The current quick patch makes the capture sheet recognize YouTube URLs and use the shared `SourceGlyph` primitive, so YouTube links no longer fall back to a one-off monogram in the capture sheet. The follow-up should centralize this with tests before adding more providers.
