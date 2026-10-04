# ISAI Master Blueprint

Status: Architecture initiated
Branch: isai-architecture-blueprint
Base: main @ 0bf1db6

## 1. Product definition

ISAI is a native-first Android music player with a premium Apple × Spotify visual language, black/gold identity, local-first playback, online music discovery, and a unified player experience.

Primary goals:
- Production-ready Android application
- Offline-first local library
- Reliable background playback
- Unified queue and now-playing state
- YouTube-connected discovery where platform policy permits
- Optional Spotify account integration where platform APIs permit
- Advanced player features: lyrics, crossfade/AutoMix, sleep timer, equalizer/effects, downloads, listening analytics, widgets, Android Auto, recommendations
- Accessible, responsive, gesture-driven UI
- Testable architecture with deterministic state

Explicitly out of scope:
- Discord Rich Presence
- Return YouTube Dislike
- Last.fm scrobbling

## 2. Architecture

Target architecture:

Presentation
  -> ViewModels / StateFlow
  -> Domain use cases
  -> Repository interfaces
  -> Data sources
  -> Android platform / remote APIs

Layers:
- app: navigation, application bootstrap, dependency injection
- core: design system, common utilities, media abstractions
- domain: entities, repository contracts, use cases
- data: Room, DataStore, MediaStore, remote providers
- playback: Media3 player, MediaSession, queue, crossfade, audio effects
- feature/home
- feature/search
- feature/library
- feature/player
- feature/queue
- feature/lyrics
- feature/downloads
- feature/playlists
- feature/settings
- feature/analytics
- feature/recommendations
- feature/onboarding

Dependency rule:
UI -> domain -> data abstractions.
UI must not directly access MediaStore, Room, HTTP, or ExoPlayer.

## 3. Source-of-truth strategy

Room is the source of truth for persistent music/library metadata.
DataStore is the source of truth for preferences.
Media3 is the source of truth for active playback position/state.
ViewModels expose immutable StateFlow to Compose.

No business-critical state should rely on localStorage or scattered mutable JavaScript state.

## 4. Data model

Initial Room entities:
- Track
- Album
- Artist
- Playlist
- PlaylistTrack
- Favorite
- ListeningEvent
- Download
- LyricsCache
- SearchHistory

Track fields include stable media ID, URI, title, artist, album, duration, artwork URI, MIME type, date added, source type, and availability.

Source types:
- LOCAL
- YOUTUBE
- SPOTIFY
- DOWNLOADED

Provider-specific IDs must never replace the app's internal stable IDs.

## 5. Playback

One authoritative PlayerManager backed by Media3.

Responsibilities:
- play/pause
- previous/next
- seek
- queue management
- shuffle
- repeat
- playback speed
- audio focus
- noisy-device handling
- background playback
- MediaSession
- notification controls
- crossfade/AutoMix orchestration
- sleep timer
- equalizer/effects integration
- playback analytics events

All player surfaces communicate through the same playback state.

## 6. Local library

Use MediaStore as the Android source.
Handle runtime permission differences by Android version.
Scan on demand and refresh safely.
Persist normalized metadata in Room.
Observe media changes where practical.
Artwork loading must be asynchronous and cached.

Required states:
- permission needed
- scanning
- empty library
- populated library
- scan error
- media removed

## 7. Online music

YouTube:
- Search through a server-side API boundary where required.
- Never expose private server credentials in the client.
- Treat YouTube playback capabilities and terms as platform constraints.
- Keep provider integration behind a MusicProvider interface.

Spotify:
- OAuth/PKCE only where appropriate.
- Tokens must be handled securely.
- Keep Spotify provider logic isolated.
- Do not claim Spotify playback support until the complete supported playback flow is validated.

Provider abstraction:
MusicProvider.search()
MusicProvider.getDetails()
MusicProvider.getRelated()
MusicProvider.getPlayableItem() when legally/platform supported.

## 8. UI/UX

Visual direction:
Apple × Spotify with ISAI's black/gold identity.

Design principles:
- dark-first premium interface
- restrained gold accents
- strong typography hierarchy
- large artwork
- soft surfaces and selective glass only where it improves hierarchy
- smooth micro-interactions
- 44dp+ touch targets
- edge-to-edge layouts
- accessibility contrast
- predictable navigation

Primary navigation:
Home / Explore / Library / Playlists / Settings

Global player:
Mini Player -> Now Playing
Now Playing supports artwork, progress, transport controls, queue, lyrics, effects, sleep timer and related actions.

## 9. Feature requirements

### Home
- personalized greeting
- recently played
- recently added
- favorites
- quick actions
- recommendations
- dynamic shelves

### Explore
- online search
- provider/category sections
- trending/discovery surfaces
- clean result cards
- loading/error/empty states

### Library
- Songs
- Albums
- Artists
- Genres where available
- Favorites
- Downloads

### Player
- artwork
- title/artist
- seek bar
- play/pause
- previous/next
- shuffle/repeat
- queue
- lyrics
- sleep timer
- equalizer/effects
- crossfade/AutoMix
- animated canvas when supported

### Playlists
- create/edit/delete
- reorder
- add/remove tracks
- smart/automatic playlists later

### Analytics
- minutes listened
- top tracks
- top artists
- listening history
- weekly/monthly summaries
- privacy-first local storage

### Recommendations
Start with deterministic local signals:
- recent plays
- favorites
- completion rate
- artist affinity
- album affinity
- time-of-day patterns
- diversity rules

AI recommendations are an extension, not a dependency for core playback.

## 10. Gestures and haptics

Gestures:
- swipe mini-player up -> Now Playing
- swipe Now Playing down -> previous surface
- horizontal artwork gesture -> previous/next where appropriate
- queue drag -> reorder
- pull-to-refresh for provider/library refresh

Haptics:
- selection
- successful action
- play/pause
- queue reorder
- toggle confirmation

Haptics must remain subtle and respect a user setting.

## 11. Reliability

Every feature needs:
- loading state
- empty state
- error state
- retry path
- offline behavior where meaningful
- lifecycle handling
- process-death recovery where appropriate

No screen should depend on a network response to render its basic shell.

## 12. Security

- no API secrets in APK/client source
- server-side secrets only
- secure token storage
- least-privilege OAuth scopes
- validate remote input
- HTTPS-only remote calls
- avoid logging tokens or personal data
- clear disconnect/revoke behavior

## 13. Testing

Required layers:
- domain unit tests
- repository/data-source tests
- ViewModel tests
- Room database tests
- playback state tests
- Compose UI tests
- provider contract tests
- instrumentation tests
- release smoke test

Critical acceptance paths:
1. fresh install
2. permission grant
3. local scan
4. play local track
5. background playback
6. lock-screen controls
7. queue
8. search
9. favorites
10. playlist creation
11. process restart
12. offline operation
13. online provider failure
14. download lifecycle
15. Android Auto where available

## 14. CI/CD

Target pipeline:
- format/lint
- static analysis
- unit tests
- instrumentation/UI tests
- dependency verification
- debug APK
- release AAB
- artifact upload
- release smoke validation

There should ultimately be one authoritative native Android build pipeline.

## 15. Migration strategy

Do not delete the existing implementation abruptly.

Phase A:
- freeze current main as baseline
- establish architecture branch
- create native package structure
- preserve working MediaStore and Media3 concepts

Phase B:
- move persistent state to Room/DataStore
- introduce DI
- create PlayerManager
- create navigation
- implement design system

Phase C:
- migrate features one by one
- local library
- player
- playlists
- search
- explore
- settings
- analytics
- lyrics
- downloads
- effects
- recommendations

Phase D:
- provider validation
- tests
- CI consolidation
- release hardening

Phase E:
- remove obsolete web-first/native-bridge paths only after parity is demonstrated.

## 16. Definition of done

A feature is not complete when its button works once.

A feature is complete only when:
- UI is final
- state survives expected lifecycle events
- loading/empty/error states exist
- accessibility is acceptable
- gestures/haptics are intentional
- tests exist for critical logic
- offline behavior is defined
- analytics events are defined where applicable
- CI passes
- release build succeeds

## 17. Immediate implementation order

1. Establish native package structure
2. Add dependency injection
3. Implement Room/DataStore repositories
4. Build authoritative PlayerManager
5. Establish navigation and design system
6. Rebuild Home/Library/Explore/Playlists/Settings
7. Rebuild Now Playing + mini-player
8. Add queue/favorites/history
9. Add lyrics
10. Add sleep timer
11. Add equalizer/effects
12. Add crossfade/AutoMix
13. Add downloads
14. Add analytics
15. Add provider integrations
16. Add recommendations
17. Add widgets/Android Auto
18. Add complete automated tests
19. Consolidate CI
20. Release hardening

## 18. Architecture decision

The existing JavaScript/Capacitor implementation is retained temporarily as a migration/reference surface. New production features should be implemented in the native architecture unless a feature explicitly requires a web/backend boundary.

This blueprint is the source of truth for the rebuild until superseded by an explicitly versioned architecture decision record.
