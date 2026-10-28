# Graph Report - Feishin  (2026-10-28)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 5134 nodes · 14106 edges · 230 communities (105 shown, 125 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 414 edges (avg confidence: 0.85)
- Token cost: 16,666 input · 2,748 output

## Graph Freshness
- Built from commit: `c447ffbe`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- UI Components and Shapes
- Color and Animation Utilities
- Shuffle Plan Logic
- Now Playing UI Components
- List and Settings UI
- Android App Widgets
- Navigation and Player Sheets
- Widget Layout Templates
- Widget Layout Previews
- Favorites and Playlist Events
- Playlist Cover Components
- Music Data Transfer Objects
- Fake Music Repository
- Music Detail Screens
- Settings and Search Storage
- Cache Dependency Injection
- Settings View Model
- Song Pager Animations
- Lyrics Data Models
- Download Management Logic
- UI State Handling
- Mini Player Gesture Physics
- Playlist Sorting and Ordering
- Media Queue Player
- Download Worker Service
- Music Repository Models
- Widget Preview Layouts
- Search Toolbar Layout
- Gradle Process Integration Tests
- File Tag Lyrics Providers
- Player Manager Service
- Gradle Run Diagnostics
- Offline Radio Fetcher
- Text Romanization Utilities
- Lyrics Scrolling Engine
- Widget Toolbar Layouts
- Widget Receiver and Activity
- Music Data Repository
- Queue Scroll Handoff
- Download Database Access
- Track Cache Storage
- Expressive Widget Toolbars
- Device Files View Model
- Login and Offline Screens
- Database Migration and Caching
- Lrclib Lyrics Provider
- Lyrics Scroll Calculations
- Music Repository Mock
- Mini Player Gesture Classification
- Radio Programming Logic
- Widget List and Grid
- Color Contrast Math
- CI Workflow Configuration
- Action List App Widget
- Long Text Widget Layout
- Gradle Process Management
- Avatar and LRU Sizing
- Download State Machine
- Library View Model
- App Navigation Rules
- Search View Model
- Library Filtering and Sorting
- Cache Limit Logic Tests
- Domain Mapping and Models
- Network and Image Module
- Media Player Service
- Offline Sync and Scrobbling
- Lyrics Editing UI
- Lyrics Tab Persistence
- Row Layout Estimation
- Lyrics View Model
- Subsonic Authentication Utilities
- Display Refresh Rate Logic
- Media Item Mapping
- Replay Gain Processing
- KRC Lyrics Parser
- Workflow Management Scripts
- Gradle Wrapper Process Tests
- Cache Size Policy
- Mobile Data Policy
- Stream URL and Prefetching
- Theme Settings Store
- Login Error Mapping
- Main Activity and Theme
- Widget UI Components
- Network Connectivity Monitor
- Artist Info Parsing Tests
- Storage Usage Math
- Queue Operations Tests
- Database Dependency Injection
- Lyrics Repository
- Queue Store Persistence Tests
- Scrobbling Submission Policy
- Queue Data Persistence
- Connection Test States
- Search Result Grouping
- Lyrics Matching Logic Tests
- Glance App Widgets
- Cache Refresh Policy
- Playback Progress Tracking
- Queue Highlighting Logic
- Embedded Lyrics Extraction
- Credential Storage
- Entity Action Sheets
- Downloads Screen UI
- Media Session Callbacks
- Cache Event Logging
- LRC Lyrics Parsing
- Cache Eviction Logic
- KuGou Lyrics Provider
- Player Dependency Injection
- Checklist App Widget
- Image Grid Widget
- Image Text List Widget
- Metadata Cache Keys
- Stream Quality Policy
- Media Controller Listeners
- Subsonic Authentication
- GitHub Issue Templates
- Checklist Data Repository
- Text Image App Widget
- Crash Guard Handler
- Library Sort Settings
- Download Notification Manager
- Full Bleed Image Widget
- Artist Track Ordering
- Voice Search Navigation
- Pager UI Dimensions
- Metadata Cache Management
- HTTP Log Redaction
- Search Toolbar Widget
- Image List Data Provider
- Long Text App Widget
- Device File Filtering
- Android Auto Testing
- Playlist Duplicate Detection
- Queue Modification Logic
- SSL Trust Management
- Android Activity Lifecycle
- Fake Track DAO
- Image Grid Data Provider
- Text Image Data Provider
- Font Sizing Utilities
- Cover Art Utilities
- Lyrics Metadata Matching
- Local Cover Art Fetcher
- Download and Auth Repositories
- Download Dependency Injection
- Application Initialization
- Login View Model
- Offline Cover Storage Tests
- Persistence State Testing
- Library Navigation Nodes
- Progress Throttle Logic
- Lyrics Cache Key Logic
- TTML and BetterLyrics Providers
- Queue Management Logic
- Theme Migration Tests
- Download Queue Management
- Cache Size Management
- Overscroll UI Effects
- Playback Source Handling
- Stream URL Provider
- Radio Station Persistence
- Widget Data Repository
- Glance Layout Components
- Settings Screen Components
- Cache Key Generation
- Scrolling Lazy Layouts
- Widget Image Utilities
- Lyrics Selection Logic
- Library Display Settings
- Storage Statistics Management
- Prefetch Window Logic
- Home Screen View Model Tests
- Bitmap Loading Utilities
- Settings Navigation Routes
- Concurrent Download Limits
- Lyrics UI Padding
- Process Management Utilities
- Cache Membership Pruning
- Storage Limit Calculations
- OpenAI Integration Config
- Offline Sync Worker
- Now Playing Bar Tinting
- Image Aspect Ratio
- Display Event Listeners
- Lyrics Parser Testing
- Dark Theme Specification
- Library Navigation Tabs
- Widget Action Broadcast Receiver
- DTO Parsing Tests
- Queue Interaction Tests
- Authentication Repository
- Playback Lifecycle Listener
- Top Bar UI
- Artist Screen Components
- Queue Shuffle Logic
- Queue Operations
- Download Resumption Logic
- Romanization Text Processing
- Refresh Rate Modes
- Library Scan Management
- Documentation and Tooling

## God Nodes (most connected - your core abstractions)
1. `SettingsStore` - 142 edges
2. `PlayerManager` - 122 edges
3. `MusicDataRepository` - 121 edges
4. `SettingsViewModel` - 106 edges
5. `Track` - 86 edges
6. `Lyrics` - 83 edges
7. `DownloadRepository` - 69 edges
8. `RoundedCornerShape` - 56 edges
9. `FakeMusicRepo` - 54 edges
10. `MusicRepository` - 51 edges

## Surprising Connections (you probably didn't know these)
- `MiniPlayer()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/MiniPlayer.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `PlaylistCoverArt()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/PlaylistArt.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `ActionTile()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/TrackActionsSheet.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `SheetRow()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/TrackActionsSheet.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `ArtistScreen()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/detail/ArtistScreen.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt

## Import Cycles
- None detected.

## Communities (230 total, 125 thin omitted)

### Community 0 - "UI Components and Shapes"
Cohesion: 0.06
Nodes (48): RoundedCornerShape, FULL, MEDIUM, ArtistAvatar(), AlbumCard(), ArtistCard(), ClickableName(), CoverImage() (+40 more)

### Community 1 - "Color and Animation Utilities"
Cohesion: 0.06
Nodes (14): Contrast, Hsl, NowPlayingColors, PlayingColors, PlayingScheme, animatedPlayingScheme(), PlayingSchemeCache, rememberPlayingScheme() (+6 more)

### Community 2 - "Shuffle Plan Logic"
Cohesion: 0.13
Nodes (3): ShufflePlan, ShufflePlanTest, BigPlaylistShuffleTest

### Community 3 - "Now Playing UI Components"
Cohesion: 0.05
Nodes (20): NowPlayingPalette, formatDuration(), MarqueeTitle(), ControlsRow(), dim75(), IntroCountdownDots(), lyricsEdgeFade(), LyricsPanel() (+12 more)

### Community 4 - "List and Settings UI"
Cohesion: 0.07
Nodes (9): ListItem(), SegmentedControl(), SettingRowText(), SettingsActionButton(), SettingsControlBlock(), SettingsNote(), UsageLegendRow(), RowText() (+1 more)

### Community 5 - "Android App Widgets"
Cohesion: 0.06
Nodes (13): NowPlayingWidget, WidgetDepsEntry, WidgetPushEffect(), WidgetLayout, COMPACT, LARGE, WidgetLayoutPicker, WidgetState (+5 more)

### Community 6 - "Navigation and Player Sheets"
Cohesion: 0.04
Nodes (12): MessageHost(), PlaylistDetailScreen(), AppTopBar(), goToLibraryTab(), MainScaffold(), Routes, TopBarSearchField(), AppNav() (+4 more)

### Community 7 - "Widget Layout Templates"
Cohesion: 0.05
Nodes (14): CheckListLayoutDimensions, CheckListLayoutTextStyles, Grid(), GridItem(), ImageGridItemData, ImageGridLayout(), ImageGridLayoutDimensions, ImageGridLayoutSize (+6 more)

### Community 8 - "Widget Layout Previews"
Cohesion: 0.33
Nodes (4): FullBleedImageLayout(), FullBleedImageLayoutPreview(), GalleryItemCard(), SnapScrollingGallery()

### Community 9 - "Favorites and Playlist Events"
Cohesion: 0.11
Nodes (7): FavoritesChanged, FavoritesStore, PlaylistEvent, PlaylistEvents, TracksAdded, MainViewModel, FavoritesStoreTest

### Community 10 - "Playlist Cover Components"
Cohesion: 0.08
Nodes (22): Collage, Favorites, Placeholder, PlaylistCover, Server, CollageCell(), PlaylistCoverArt(), PlaylistCoverArtSquare() (+14 more)

### Community 11 - "Music Data Transfer Objects"
Cohesion: 0.08
Nodes (44): AlbumDetailDto, AlbumDto, AlbumDtoListSerializer, AlbumList2Dto, AlbumListDto, ArtistDetailDto, ArtistDto, ArtistDtoListSerializer (+36 more)

### Community 13 - "Music Detail Screens"
Cohesion: 0.06
Nodes (11): ActionTarget, AlbumDetailScreen(), AlbumHeader(), CircleIconButton(), TrackListControlsRow(), GenreScreen(), GenreViewModel, EmptyBox() (+3 more)

### Community 15 - "Cache Dependency Injection"
Cohesion: 0.07
Nodes (14): CacheModule, AlbumSnap, DiskHomeCache, HomeCache, HomeSnapshot, InMemoryHomeCache, TrackSnap, AlbumTarget (+6 more)

### Community 18 - "Lyrics Data Models"
Cohesion: 0.10
Nodes (15): DebugLyricsFixtures, fromSynced(), LyricLine, Lyrics, LyricsKind, INSTRUMENTAL, NONE, PLAIN (+7 more)

### Community 19 - "Download Management Logic"
Cohesion: 0.06
Nodes (5): removeIf(), DownloadGroupEntity, DownloadProgress, DownloadRepository, observeAllSnapshot()

### Community 20 - "UI State Handling"
Cohesion: 0.07
Nodes (13): Data, Empty, Err, Error, Loading, Ok, Result, UiState (+5 more)

### Community 21 - "Mini Player Gesture Physics"
Cohesion: 0.10
Nodes (3): SheetPhysics, SheetPhysicsTest, LyricsAnimationTest

### Community 23 - "Playlist Sorting and Ordering"
Cohesion: 0.09
Nodes (9): PlaylistOrdering, PlaylistSort, PlaylistSortKey, ARTIST, DATE_ADDED, DURATION, PLAYLIST_ORDER, TITLE (+1 more)

### Community 24 - "Media Queue Player"
Cohesion: 0.09
Nodes (4): BoundedQueuePlayer, Timeline, BoundedQueuePlayerTest, FakeTimeline

### Community 25 - "Download Worker Service"
Cohesion: 0.14
Nodes (6): DownloadWorker, Outcome, DONE, FAILED, PAUSED, RETRY

### Community 26 - "Music Repository Models"
Cohesion: 0.06
Nodes (6): PlaylistBatch, ListSerializer(), MusicRepository, RepoModule, PlaylistBatchTest, SyncedLyricsTest

### Community 27 - "Widget Preview Layouts"
Cohesion: 0.09
Nodes (18): HorizontalContent(), Image(), ImageData, maybeClickable(), NoDataContent(), TextData, TextStack(), TextWithImageData (+10 more)

### Community 28 - "Search Toolbar Layout"
Cohesion: 0.15
Nodes (13): SearchBar(), SearchIconButton(), SearchToolBarButton, SearchToolBarLayout(), SearchToolBarLayoutDimens, SearchToolBarLayoutSize, HeaderTwoRowGrid, HorizontalRow (+5 more)

### Community 29 - "Gradle Process Integration Tests"
Cohesion: 0.09
Nodes (4): GradleRunProcessTest, GradleRunTestCase, GradleRunWorkflowTest, process_is_running()

### Community 30 - "File Tag Lyrics Providers"
Cohesion: 0.13
Nodes (6): FileTagLyricsProvider, PaxsenixLyricsProvider, LyricsProvider, Hit, LyricsProbeTest, Row

### Community 32 - "Gradle Run Diagnostics"
Cohesion: 0.10
Nodes (28): add_fingerprint(), append_run(), bounded_summary(), Diagnostics, display_command(), effective_command(), emit_summary(), extract_diagnostics() (+20 more)

### Community 33 - "Offline Radio Fetcher"
Cohesion: 0.07
Nodes (6): OfflineRadio, RadioFetcher, toDomainTrack(), RadioFakeRepo, RadioFetcherTest, track()

### Community 35 - "Lyrics Scrolling Engine"
Cohesion: 0.10
Nodes (8): computeScrollDelta(), computeScrollDurationMs(), computeScrollOffset(), evaluateRetargetDelta(), LyricsDisplayTokens, shouldAutoScroll(), LyricsLineGapTest, LyricsScrollEngineTest

### Community 36 - "Widget Toolbar Layouts"
Cohesion: 0.08
Nodes (15): FluidContentIconButton(), FluidHeaderAppIcon(), FluidHeaderIconButton(), Header(), ToolBarButton, ToolBarLayout(), ToolBarLayoutDimens, ToolBarLayoutSize (+7 more)

### Community 37 - "Widget Receiver and Activity"
Cohesion: 0.08
Nodes (6): CanonicalLayoutActivity, CanonicalLayoutRow(), CanonicalLayoutRowData, Content(), Header(), requestPin()

### Community 39 - "Queue Scroll Handoff"
Cohesion: 0.14
Nodes (6): ListHandoff, PassToList, QueueHandoff, TakeBySheet, NestedScrollConnection, QueueHandoffTest

### Community 40 - "Download Database Access"
Cohesion: 0.12
Nodes (3): DownloadDao, DownloadEntity, DownloadGroupDao

### Community 41 - "Track Cache Storage"
Cohesion: 0.09
Nodes (5): CacheBytes, CachedTrackStore, Media3CacheBytes, CachedTrackDao, CachedTrackEntity

### Community 42 - "Expressive Widget Toolbars"
Cohesion: 0.15
Nodes (15): AllButtonsScaledLayout(), CenterButton(), CenterButtonOnlyLayout(), checkCornerButtonsListSize(), CornerButton(), CornerButtonsGrid(), ExpressiveToolBarButton, ExpressiveToolbarLayout() (+7 more)

### Community 43 - "Device Files View Model"
Cohesion: 0.10
Nodes (3): DeviceFilesState, DeviceFilesViewModel, ContentObserver

### Community 44 - "Login and Offline Screens"
Cohesion: 0.05
Nodes (7): DownloadIndicator(), OfflineBanner(), LoginMessageCard(), LoginScreen(), VusicColors, VusicControlColors, VusicControlDefaults

### Community 45 - "Database Migration and Caching"
Cohesion: 0.09
Nodes (6): LyricsCacheMigrationTest, CachedCoverDao, CachedCoverEntity, CachedMetadataEntity, LyricsCacheDao, LyricsCacheEntity

### Community 46 - "Lrclib Lyrics Provider"
Cohesion: 0.11
Nodes (4): LrclibResponseDto, LrclibLyricsProvider, LrclibMatcher, LrclibMatcherTest

### Community 47 - "Lyrics Scroll Calculations"
Cohesion: 0.13
Nodes (12): computeEdgeFadeAlpha(), computeLyricsAnchorPx(), computeLyricsBottomPadding(), computeLyricsScrollDelta(), computeLyricsScrollOffset(), resolveReturnScrollAction(), ScrollAction, ANIMATE (+4 more)

### Community 49 - "Mini Player Gesture Classification"
Cohesion: 0.14
Nodes (4): MiniPlayerGesture, MiniPlayer(), MiniPlayerRing(), MiniPlayerGestureTest

### Community 50 - "Radio Programming Logic"
Cohesion: 0.13
Nodes (5): RadioMemory, RadioProgrammer, RadioTrigger, RadioProgrammerTest, track()

### Community 51 - "Widget List and Grid"
Cohesion: 0.10
Nodes (19): ActionListLayout(), Content(), GridView(), ListView(), CheckListItem(), CheckListLayout(), CheckListLayoutSize, Large (+11 more)

### Community 52 - "Color Contrast Math"
Cohesion: 0.16
Nodes (3): VusicColorMath, LyricsMenuContrastTest, VusicDesignTest

### Community 53 - "CI Workflow Configuration"
Cohesion: 0.06
Nodes (30): name, runs-on, steps, timeout-minutes, concurrency, cancel-in-progress, group, jobs (+22 more)

### Community 54 - "Action List App Widget"
Cohesion: 0.11
Nodes (3): ActionListAppWidget, ActionListAppWidgetAppWidgetReceiver, FakeActionListDataRepository

### Community 55 - "Long Text Widget Layout"
Cohesion: 0.16
Nodes (11): LongTextLayout(), LongTextLayoutData, LongTextLayoutPreview(), LongTextLayoutSize, Normal, XSmall, maybeClickable(), showTitle() (+3 more)

### Community 57 - "Avatar and LRU Sizing"
Cohesion: 0.13
Nodes (4): AvatarFallback, Entry, LruSizer, M6PartATest

### Community 58 - "Download State Machine"
Cohesion: 0.13
Nodes (8): DownloadStateMachine, DownloadStatus, COMPLETED, DOWNLOADING, FAILED, PAUSED, QUEUED, DownloadStateMachineTest

### Community 60 - "App Navigation Rules"
Cohesion: 0.15
Nodes (4): AppNavigationBar(), goToTab(), NavRules, NavRulesTest

### Community 61 - "Search View Model"
Cohesion: 0.10
Nodes (7): SearchFilter, ALBUMS, ALL, ARTISTS, PLAYLISTS, SONGS, SearchViewModel

### Community 62 - "Library Filtering and Sorting"
Cohesion: 0.17
Nodes (3): LibraryOrdering, album(), LibraryOrderingTest

### Community 64 - "Domain Mapping and Models"
Cohesion: 0.19
Nodes (18): SyncedLyrics, toDomain(), Album, AlbumDetail, Artist, ArtistDetail, ArtistInfo, Genre (+10 more)

### Community 67 - "Offline Sync and Scrobbling"
Cohesion: 0.11
Nodes (11): PendingActionDao, PendingActionEntity, OfflineSyncStore, PendingActionType, SCROBBLE, STAR, STAR_ALBUM, STAR_ARTIST (+3 more)

### Community 68 - "Lyrics Editing UI"
Cohesion: 0.16
Nodes (9): EditLyricsDialog(), formatLyricsForCopy(), formatLyricsForEdit(), formatToLrc(), LyricsMenuSheet(), LyricsOffsetDialog(), MenuSwitchRow(), parseTextSizeToPercent() (+1 more)

### Community 70 - "Row Layout Estimation"
Cohesion: 0.24
Nodes (5): Beside, NextLineEnd, Placement, RowLayout, RowLayoutTest

### Community 71 - "Lyrics View Model"
Cohesion: 0.14
Nodes (8): LyricsCandidate, Instrumental, Loading, LyricsUi, LyricsViewModel, None, Plain, Synced

### Community 73 - "Display Refresh Rate Logic"
Cohesion: 0.25
Nodes (3): DisplayModeInfo, RefreshRate, RefreshRateTest

### Community 74 - "Media Item Mapping"
Cohesion: 0.20
Nodes (3): TrackMediaInfo, TrackMediaItems, TrackMediaItemsTest

### Community 75 - "Replay Gain Processing"
Cohesion: 0.14
Nodes (6): ReplayGain, ReplayGainMode, ALBUM, OFF, TRACK, ReplayGainTest

### Community 76 - "KRC Lyrics Parser"
Cohesion: 0.21
Nodes (9): KrcLyricsParser, LyricsSource, BETTER_LYRICS, CACHE, FILE_TAGS, KUGOU, LRCLIB, PAXSENIX (+1 more)

### Community 77 - "Workflow Management Scripts"
Cohesion: 0.19
Nodes (16): create_workflow(), default_root(), finish_locked(), finish_workflow(), finished_path(), lock_path(), main(), managed_root() (+8 more)

### Community 78 - "Gradle Wrapper Process Tests"
Cohesion: 0.09
Nodes (3): GradleRunHeartbeatTest, GradleRunWindowsProcessTest, ProcessAssertionTest

### Community 80 - "Mobile Data Policy"
Cohesion: 0.09
Nodes (4): MobileDataPolicy, DownloadsUi, DownloadsViewModel, MobileDataPolicyTest

### Community 82 - "Theme Settings Store"
Cohesion: 0.14
Nodes (4): ThemeMode, DARK, LIGHT, SYSTEM

### Community 83 - "Login Error Mapping"
Cohesion: 0.16
Nodes (3): LoginErrorMapper, LoginFailure, LoginErrorMapperTest

### Community 85 - "Widget UI Components"
Cohesion: 0.19
Nodes (9): VerticalListItem(), PillShapedButton(), RectangularIconButton(), Action, CLEAR_QUEUE, NEXT, NONE, OPEN (+1 more)

### Community 86 - "Network Connectivity Monitor"
Cohesion: 0.16
Nodes (8): NetworkClass, MOBILE, OTHER, WIFI, NetworkMonitor, onAvailable(), onCapabilitiesChanged(), onLost()

### Community 87 - "Artist Info Parsing Tests"
Cohesion: 0.17
Nodes (4): stripHtmlBio(), flowNow(), M45Test, track()

### Community 88 - "Storage Usage Math"
Cohesion: 0.25
Nodes (4): Result, Segment, UsageBarMath, UsageBarMathTest

### Community 94 - "Queue Data Persistence"
Cohesion: 0.22
Nodes (3): PersistedTrack, QueueSnapshot, QueueStore

### Community 95 - "Connection Test States"
Cohesion: 0.33
Nodes (5): Failed, Idle, Ok, Testing, TestState

### Community 96 - "Search Result Grouping"
Cohesion: 0.20
Nodes (6): Album, Artist, SearchGrouping, TopHit, playlist(), SearchGroupingTest

### Community 99 - "Cache Refresh Policy"
Cohesion: 0.26
Nodes (3): CachePolicy, CachePolicyTest, FakeClock

### Community 101 - "Queue Highlighting Logic"
Cohesion: 0.25
Nodes (3): QueueHighlight, State, QueueHighlightTest

### Community 103 - "Credential Storage"
Cohesion: 0.19
Nodes (3): Credentials, CredentialsStore, HostRewriteInterceptor

### Community 105 - "Entity Action Sheets"
Cohesion: 0.21
Nodes (22): speedDialKey(), ActionTile(), ActionTiles(), Album, AlbumDetailsDialog(), AlbumSheetContent(), Artist, ArtistSheetContent() (+14 more)

### Community 107 - "Downloads Screen UI"
Cohesion: 0.44
Nodes (7): ActiveRow(), DoneRow(), DownloadsScreen(), ProgressThumb(), SectionLabel(), StorageRow(), DownloadRow

### Community 109 - "Cache Event Logging"
Cohesion: 0.31
Nodes (3): EventListener, UpstreamLogger, DataSource

### Community 110 - "LRC Lyrics Parsing"
Cohesion: 0.25
Nodes (3): LrcParser, Tag, LrcParserTest

### Community 112 - "KuGou Lyrics Provider"
Cohesion: 0.24
Nodes (7): KugouCandidate, KugouCandidatesResponse, KugouDownloadResponse, KuGouLyricsProvider, KugouSearchData, KugouSearchResponse, KugouSearchSong

### Community 115 - "Image Grid Widget"
Cohesion: 0.19
Nodes (3): ImageGridAppWidget, ImageGridAppWidgetReceiver, WidgetContent()

### Community 121 - "GitHub Issue Templates"
Cohesion: 0.12
Nodes (16): description, label, options, placeholder, render, body, attributes, id (+8 more)

### Community 125 - "Library Sort Settings"
Cohesion: 0.21
Nodes (4): LibrarySorts, Option, SortChoice, LibrarySortTest

### Community 131 - "Metadata Cache Management"
Cohesion: 0.14
Nodes (3): MetadataCache, MetadataCacheDao, FakeCacheDao

### Community 133 - "Search Toolbar Widget"
Cohesion: 0.15
Nodes (3): SearchToolBarAppWidget, SearchToolBarAppWidgetReceiver, ActionUtils

### Community 138 - "Playlist Duplicate Detection"
Cohesion: 0.27
Nodes (5): Partition, PlaylistDuplicates, PendingDupCheck, PlaylistDuplicatesTest, track()

### Community 149 - "Download and Auth Repositories"
Cohesion: 0.07
Nodes (5): QualityPolicy, VLog, CoverArtUrlProvider, DownloadScheduler, NowPlayingNudge

### Community 153 - "Offline Cover Storage Tests"
Cohesion: 0.41
Nodes (3): FakeCoverStore, FakeDownload, OfflineCoverStoreTest

### Community 158 - "TTML and BetterLyrics Providers"
Cohesion: 0.14
Nodes (4): TtmlLyricsParser, BetterLyricsProvider, BetterLyricsResponse, TtmlLyricsParserTest

### Community 160 - "Theme Migration Tests"
Cohesion: 0.33
Nodes (3): migrateStoredTheme(), resolveDark(), ThemeModeTest

### Community 161 - "Download Queue Management"
Cohesion: 0.47
Nodes (3): ClearDownloadQueueTest, FakeDownload, FakeDownloadQueueManager

### Community 164 - "Playback Source Handling"
Cohesion: 0.24
Nodes (5): Kind, LOCAL, STREAM, PlaybackSource, PlaybackSourceTest

### Community 168 - "Glance Layout Components"
Cohesion: 0.60
Nodes (5): HeaderTwoRowGrid(), SideBarTwoRowGrid(), SpacedColumn(), SpacedRow(), TwoRowGrid()

### Community 169 - "Settings Screen Components"
Cohesion: 0.21
Nodes (19): SettingControlRow(), SettingsCard(), SettingsDivider(), SettingsSection(), SwitchRow(), AboutScreen(), AppearanceScreen(), BitrateChoiceRow() (+11 more)

### Community 170 - "Cache Key Generation"
Cohesion: 0.27
Nodes (3): CachedMembership, StableCacheKey, StableCacheKeyTest

### Community 171 - "Scrolling Lazy Layouts"
Cohesion: 0.12
Nodes (3): ActionDemonstrationActivity, RoundedScrollingLazyColumn(), RoundedScrollingLazyVerticalGrid()

### Community 173 - "Lyrics Selection Logic"
Cohesion: 0.19
Nodes (3): LyricsSelection, NavidromeLyricsProvider, LyricsSelectionTest

### Community 174 - "Library Display Settings"
Cohesion: 0.11
Nodes (4): LayoutMode, GRID, LIST, Settings

### Community 179 - "Settings Navigation Routes"
Cohesion: 0.25
Nodes (8): SettingsRoute, ABOUT, APPEARANCE, CONNECTIVITY, HOME, SERVER, SOUND, STORAGE

### Community 182 - "Process Management Utilities"
Cohesion: 0.33
Nodes (3): emit_heartbeat(), terminate_child(), wait_for_child()

### Community 185 - "OpenAI Integration Config"
Cohesion: 0.40
Nodes (4): interface, default_prompt, display_name, short_description

### Community 198 - "Library Navigation Tabs"
Cohesion: 0.15
Nodes (8): tabLabel(), LibraryTab, ALBUMS, ARTISTS, DOWNLOADS, FAVORITES, PLAYLISTS, SONGS

### Community 206 - "Artist Screen Components"
Cohesion: 0.31
Nodes (5): ArtistCollapsedTopBar(), ArtistControls(), ArtistHero(), ArtistScreen(), CircleIconButtonShim()

### Community 211 - "Refresh Rate Modes"
Cohesion: 0.40
Nodes (4): RefreshRateMode, AUTO, MAX, STANDARD

## Knowledge Gaps
- **102 isolated node(s):** `PassToList`, `None`, `TracksAdded`, `FULL`, `MEDIUM` (+97 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 842 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **125 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MusicDataRepository` connect `Music Data Repository` to `UI Components and Shapes`, `Now Playing UI Components`, `Navigation and Player Sheets`, `Favorites and Playlist Events`, `Playlist Cover Components`, `Fake Music Repository`, `Music Detail Screens`, `Cache Dependency Injection`, `Settings View Model`, `Song Pager Animations`, `Download Management Logic`, `UI State Handling`, `Download and Auth Repositories`, `Music Repository Models`, `Player Manager Service`, `Offline Radio Fetcher`, `Music Repository Mock`, `Library View Model`, `Search View Model`, `Domain Mapping and Models`, `Media Player Service`, `Offline Sync and Scrobbling`, `Artist Screen Components`, `Player Background Service`, `Entity Action Sheets`, `Downloads Screen UI`?**
  _High betweenness centrality (0.128) - this node is a cross-community bridge._
- **What connects `PassToList`, `None`, `TracksAdded` to the rest of the system?**
  _102 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `UI Components and Shapes` be split into smaller, more focused modules?**
  _Cohesion score 0.056679151061173536 - nodes in this community are weakly interconnected._
- **Why does `SettingsStore` connect `Settings and Search Storage` to `UI Components and Shapes`, `Now Playing UI Components`, `Favorites and Playlist Events`, `Music Detail Screens`, `Cache Dependency Injection`, `Settings View Model`, `Lyrics Data Models`, `Download Management Logic`, `Download and Auth Repositories`, `Playlist Sorting and Ordering`, `Login View Model`, `Download Worker Service`, `Persistence State Testing`, `Player Manager Service`, `Theme Migration Tests`, `Text Romanization Utilities`, `Device Files View Model`, `Login and Offline Screens`, `Library Display Settings`, `Home Screen View Model Tests`, `Library View Model`, `Search View Model`, `Domain Mapping and Models`, `Media Player Service`, `Lyrics Editing UI`, `Lyrics Tab Persistence`, `Lyrics View Model`, `Cache Size Policy`, `Stream URL and Prefetching`, `Theme Settings Store`, `Artist Info Parsing Tests`, `Lyrics Repository`, `Lyrics Matching Logic Tests`, `Player Background Service`, `Entity Action Sheets`, `Library Sort Settings`?**
  _High betweenness centrality (0.096) - this node is a cross-community bridge._
- **Should `Color and Animation Utilities` be split into smaller, more focused modules?**
  _Cohesion score 0.05672948791166952 - nodes in this community are weakly interconnected._
- **Why does `PlayerManager` connect `Player Manager Service` to `UI Components and Shapes`, `Now Playing UI Components`, `Navigation and Player Sheets`, `Favorites and Playlist Events`, `Playlist Cover Components`, `Queue Modification Logic`, `Music Detail Screens`, `Settings and Search Storage`, `Song Pager Animations`, `Download and Auth Repositories`, `Offline Radio Fetcher`, `Music Data Repository`, `Device Files View Model`, `Mini Player Gesture Classification`, `Radio Programming Logic`, `Media Player Service`, `Artist Screen Components`, `Stream URL and Prefetching`, `Playback Controls`, `Queue Data Persistence`, `Credential Storage`, `Entity Action Sheets`, `Media Controller Listeners`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Should `Shuffle Plan Logic` be split into smaller, more focused modules?**
  _Cohesion score 0.12834224598930483 - nodes in this community are weakly interconnected._