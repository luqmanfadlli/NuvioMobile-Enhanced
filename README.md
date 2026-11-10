# Nuvio Enhanced (IMDb build)

A personal fork of [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced), which is itself an unofficial fork of the official [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile).

For features, Android builds, support and everything else, see the [original fork](https://github.com/luqmanfadlli/NuvioMobile-Enhanced).

Not affiliated with the Nuvio team or luqmanfadlli. Please don't report issues from this build to them.

## What's changed

- **IMDb-only episode ratings.** Episode cards show the IMDb rating (and your own rating) only. The TMDB badge is removed.
- **Bigger IMDb logo.** The IMDb logo on episodes is drawn wide (2:1), the same as the official Nuvio app, instead of squashed into a square.
- **Automatic updates.** Every 6 hours this fork checks the original fork for a new release, merges it in with the changes above, builds the iOS app and publishes it.

Everything else is the same as the original fork.

## Install (iOS)

Add this source in Feather (or AltStore / SideStore):

```
https://raw.githubusercontent.com/lalith14/NuvioMobile-Enhanced/feather-source/source.json
```

Or download the IPA from [Releases](https://github.com/lalith14/NuvioMobile-Enhanced/releases/latest).

The IPA is unsigned. To keep your data, sign it with the same certificate as your installed Nuvio and install it over the existing app.
