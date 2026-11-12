# Nuvio One 1.1.0-beta.13.4

## Playback stability

- Fixed OneTube freezing and stutter caused by YouTube supplying 150+ auto-translated subtitle tracks per video. OneTube now loads only the video's own captions, your preferred language, and English. In testing, a 158-track video now loads 3.
- Brought over the latest SmartTube playback-engine fixes: thread-pool handling, fallback URL validation, and live SABR handling.
- Feeds refresh when you return to OneTube after 5 minutes.

## OneTube player

- Smaller video title with channel name, views, upload date, likes and dislikes.
- Resolution, codec and playback-speed readout, for example `1440x1080 · VP9 · 1.25x`.
- Play receives focus when a video starts and when the video-info panel closes; Up reaches the action row.
- Long-press Speed switches between 1x and your last speed.
- Long card titles now scroll.
- Primary YouTube actions now sit in the row above the progress bar.

## Navigation

- Back at any top-level screen now shows "Press back again to exit"; the second press closes the app.
- Back no longer reopens the sidebar or jumps to the section selected at startup.
- Down from a tab returns to the first card, even after scrolling right.
- The Channels list collapses after selecting a channel; Back restores it.
- Search now shows channel cards and starts new pages at the top.
- Added playlist deletion, "Not interested", and long-press card menus for signed-in OneTube accounts.

## Audio

- Added **OneTube → Settings → Playback → Volume offset**: -12 dB to +6 dB, so OneTube can be matched to the rest of the app. Only OneTube playback is affected.

## Plugins

- Improved plugin-list reliability when enabling a plugin while a repository is refreshing.

## Updates

- New centered update card with Download now, Later, and live download progress.
- Fixed the progress bar staying at 0% while the update was already downloading in the background.
