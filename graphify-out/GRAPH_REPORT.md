# Graph Report - Feishin  (2026-10-28)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 5132 nodes · 14105 edges · 198 communities (97 shown, 101 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 414 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b5f3cc19`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Community 0
- Community 1
- Community 2
- Community 3
- Community 4
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9
- Community 10
- Community 11
- Community 12
- Community 13
- Community 14
- Community 15
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 40
- Community 41
- Community 42
- Community 43
- Community 44
- Community 45
- Community 46
- Community 47
- Community 48
- Community 49
- Community 50
- Community 51
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56
- Community 57
- Community 58
- Community 59
- Community 60
- Community 61
- Community 62
- Community 63
- Community 64
- Community 65
- Community 66
- Community 67
- Community 68
- Community 69
- Community 70
- Community 71
- Community 72
- Community 73
- Community 74
- Community 75
- Community 76
- Community 77
- Community 78
- Community 79
- Community 80
- Community 81
- Community 82
- Community 83
- Community 84
- Community 85
- Community 86
- Community 87
- Community 88
- Community 89
- Community 90
- Community 91
- Community 92
- Community 93
- Community 94
- Community 95
- Community 96
- Community 97
- Community 98
- Community 99
- Community 100
- Community 101
- Community 102
- Community 103
- Community 105
- Community 107
- Community 108
- Community 109
- Community 110
- Community 111
- Community 112
- Community 113
- Community 114
- Community 115
- Community 116
- Community 117
- Community 118
- Community 119
- Community 120
- Community 121
- Community 122
- Community 123
- Community 124
- Community 125
- Community 126
- Community 127
- Community 128
- Community 129
- Community 130
- Community 131
- Community 132
- Community 133
- Community 134
- Community 135
- Community 136
- Community 137
- Community 138
- Community 140
- Community 141
- Community 142
- Community 143
- Community 144
- Community 145
- Community 146
- Community 147
- Community 148
- Community 149
- Community 150
- Community 151
- Community 152
- Community 153
- Community 154
- Community 155
- Community 156
- Community 157
- Community 158
- Community 159
- Community 160
- Community 161
- Community 162
- Community 163
- Community 164
- Community 165
- Community 166
- Community 167
- Community 168
- Community 169
- Community 170
- Community 171
- Community 172
- Community 173
- Community 175
- Community 176
- Community 177
- Community 178
- Community 179
- Community 180
- Community 181
- Community 182
- Community 183
- Community 184
- Community 185
- Community 186
- Community 187
- Community 188
- Community 189
- Community 190
- Community 193

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
- `ErrorState()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/Components.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `MiniPlayer()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/MiniPlayer.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `PlaylistCoverArt()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/PlaylistArt.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `ActionTile()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/TrackActionsSheet.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `SheetRow()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/TrackActionsSheet.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt

## Import Cycles
- None detected.

## Communities (198 total, 101 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.05
Nodes (46): RoundedCornerShape, FULL, MEDIUM, ArtistAvatar(), AlbumCard(), ArtistCard(), ClickableName(), CoverImage() (+38 more)

### Community 1 - "Community 1"
Cohesion: 0.06
Nodes (14): Contrast, Hsl, NowPlayingColors, PlayingColors, PlayingScheme, animatedPlayingScheme(), PlayingSchemeCache, rememberPlayingScheme() (+6 more)

### Community 2 - "Community 2"
Cohesion: 0.05
Nodes (10): QueueCommit, QueueOps, ShufflePlan, M6QueueTest, Entry, QueueOpsTest, ShufflePlanTest, BigPlaylistShuffleTest (+2 more)

### Community 3 - "Community 3"
Cohesion: 0.05
Nodes (20): NowPlayingPalette, formatDuration(), MarqueeTitle(), ControlsRow(), dim75(), IntroCountdownDots(), lyricsEdgeFade(), LyricsPanel() (+12 more)

### Community 4 - "Community 4"
Cohesion: 0.05
Nodes (31): SegmentedControl(), SettingControlRow(), SettingRowText(), SettingsActionButton(), SettingsControlBlock(), SettingsNote(), UsageLegendRow(), RowText() (+23 more)

### Community 5 - "Community 5"
Cohesion: 0.06
Nodes (13): AutoBrowseDeviceTest, BrowserListener, WidgetActionReceiver, WidgetLayout, COMPACT, LARGE, WidgetLayoutPicker, WidgetState (+5 more)

### Community 6 - "Community 6"
Cohesion: 0.05
Nodes (11): MessageHost(), LoginMessageCard(), LoginScreen(), AppNavigationBar(), AppTopBar(), goToLibraryTab(), goToTab(), MainScaffold() (+3 more)

### Community 7 - "Community 7"
Cohesion: 0.05
Nodes (20): ActionListLayout(), Content(), GridView(), ListView(), CheckListItem(), CheckListLayout(), CheckListLayoutDimensions, CheckListLayoutSize (+12 more)

### Community 8 - "Community 8"
Cohesion: 0.05
Nodes (8): ListItem(), RoundedScrollingLazyColumn(), FullBleedImageLayout(), FullBleedImageLayoutPreview(), GalleryItemCard(), SnapScrollingGallery(), StatusBarSpacer(), TopLevelTitleBar

### Community 9 - "Community 9"
Cohesion: 0.07
Nodes (31): FavoritesChanged, FavoritesStore, PlaylistEvent, PlaylistEvents, TracksAdded, speedDialKey(), ActionTile(), ActionTiles() (+23 more)

### Community 10 - "Community 10"
Cohesion: 0.06
Nodes (24): Favorites, Placeholder, PlaylistCover, EmptyState(), DownloadIndicator(), OfflineBanner(), CollageCell(), PlaylistCoverArt() (+16 more)

### Community 11 - "Community 11"
Cohesion: 0.10
Nodes (43): AlbumDetailDto, AlbumDto, AlbumDtoListSerializer, AlbumList2Dto, AlbumListDto, ArtistDetailDto, ArtistDto, ArtistDtoListSerializer (+35 more)

### Community 12 - "Community 12"
Cohesion: 0.06
Nodes (5): album(), FakeCacheBytes, FakeMusicRepo, track(), ViewModelStateTest

### Community 13 - "Community 13"
Cohesion: 0.06
Nodes (21): ActionDemonstrationActivity, IconButton(), ErrorState(), rememberSavableListState(), ArtistCollapsedTopBar(), ArtistControls(), ArtistHero(), ArtistScreen() (+13 more)

### Community 15 - "Community 15"
Cohesion: 0.07
Nodes (14): CacheModule, AlbumSnap, DiskHomeCache, HomeCache, HomeSnapshot, InMemoryHomeCache, TrackSnap, AlbumTarget (+6 more)

### Community 17 - "Community 17"
Cohesion: 0.05
Nodes (5): MiniPlayer(), MiniPlayerRing(), Motion, TopBarAction, TopBarController

### Community 18 - "Community 18"
Cohesion: 0.09
Nodes (15): DebugLyricsFixtures, fromSynced(), LyricLine, Lyrics, LyricsKind, INSTRUMENTAL, NONE, PLAIN (+7 more)

### Community 19 - "Community 19"
Cohesion: 0.06
Nodes (5): removeIf(), DownloadGroupEntity, DownloadProgress, DownloadRepository, observeAllSnapshot()

### Community 20 - "Community 20"
Cohesion: 0.06
Nodes (15): Data, Empty, Err, Error, Loading, Ok, Result, UiState (+7 more)

### Community 21 - "Community 21"
Cohesion: 0.09
Nodes (3): SheetPhysics, SheetPhysicsTest, LyricsAnimationTest

### Community 23 - "Community 23"
Cohesion: 0.09
Nodes (9): PlaylistOrdering, PlaylistSort, PlaylistSortKey, ARTIST, DATE_ADDED, DURATION, PLAYLIST_ORDER, TITLE (+1 more)

### Community 24 - "Community 24"
Cohesion: 0.08
Nodes (6): BoundedQueuePlayer, Timeline, BoundedQueuePlayerTest, FakeTimeline, Fake, QueueTapToggleTest

### Community 25 - "Community 25"
Cohesion: 0.06
Nodes (8): DownloadScheduler, DownloadWorker, Outcome, DONE, FAILED, PAUSED, RETRY, OfflineSyncWorker

### Community 27 - "Community 27"
Cohesion: 0.09
Nodes (18): HorizontalContent(), Image(), ImageData, maybeClickable(), NoDataContent(), TextData, TextStack(), TextWithImageData (+10 more)

### Community 28 - "Community 28"
Cohesion: 0.08
Nodes (15): SearchBar(), SearchIconButton(), SearchToolBarButton, SearchToolBarLayout(), SearchToolBarLayoutDimens, SearchToolBarLayoutSize, HeaderTwoRowGrid, HorizontalRow (+7 more)

### Community 29 - "Community 29"
Cohesion: 0.09
Nodes (4): GradleRunProcessTest, GradleRunTestCase, GradleRunWorkflowTest, process_is_running()

### Community 30 - "Community 30"
Cohesion: 0.08
Nodes (8): BetterLyricsProvider, BetterLyricsResponse, FileTagLyricsProvider, PaxsenixLyricsProvider, LyricsProvider, Hit, LyricsProbeTest, Row

### Community 31 - "Community 31"
Cohesion: 0.11
Nodes (3): ClearedQueue, PlayerManager, QueueItem

### Community 32 - "Community 32"
Cohesion: 0.10
Nodes (28): add_fingerprint(), append_run(), bounded_summary(), Diagnostics, display_command(), effective_command(), emit_summary(), extract_diagnostics() (+20 more)

### Community 33 - "Community 33"
Cohesion: 0.08
Nodes (4): RadioFetcher, RadioFakeRepo, RadioFetcherTest, track()

### Community 34 - "Community 34"
Cohesion: 0.09
Nodes (3): Romanizer, RomanizerJoiningTest, RomanizerTest

### Community 35 - "Community 35"
Cohesion: 0.10
Nodes (8): computeScrollDelta(), computeScrollDurationMs(), computeScrollOffset(), evaluateRetargetDelta(), LyricsDisplayTokens, shouldAutoScroll(), LyricsLineGapTest, LyricsScrollEngineTest

### Community 36 - "Community 36"
Cohesion: 0.10
Nodes (15): FluidContentIconButton(), FluidHeaderAppIcon(), FluidHeaderIconButton(), Header(), ToolBarButton, ToolBarLayout(), ToolBarLayoutDimens, ToolBarLayoutSize (+7 more)

### Community 37 - "Community 37"
Cohesion: 0.07
Nodes (6): CanonicalLayoutActivity, CanonicalLayoutRow(), CanonicalLayoutRowData, Content(), Header(), requestPin()

### Community 39 - "Community 39"
Cohesion: 0.14
Nodes (6): ListHandoff, PassToList, QueueHandoff, TakeBySheet, NestedScrollConnection, QueueHandoffTest

### Community 40 - "Community 40"
Cohesion: 0.12
Nodes (3): DownloadDao, DownloadEntity, DownloadGroupDao

### Community 41 - "Community 41"
Cohesion: 0.09
Nodes (6): CacheBytes, CachedTrackStore, Media3CacheBytes, toTrack(), CachedTrackDao, CachedTrackEntity

### Community 42 - "Community 42"
Cohesion: 0.14
Nodes (14): AllButtonsScaledLayout(), CenterButton(), CenterButtonOnlyLayout(), checkCornerButtonsListSize(), CornerButton(), CornerButtonsGrid(), ExpressiveToolBarButton, ExpressiveToolbarLayout() (+6 more)

### Community 43 - "Community 43"
Cohesion: 0.11
Nodes (3): DeviceFilesState, DeviceFilesViewModel, ContentObserver

### Community 44 - "Community 44"
Cohesion: 0.10
Nodes (12): Grid(), GridItem(), ImageGridItemData, ImageGridLayout(), ImageGridLayoutDimensions, ImageGridLayoutSize, Large, Medium (+4 more)

### Community 45 - "Community 45"
Cohesion: 0.09
Nodes (6): LyricsCacheMigrationTest, CachedCoverDao, CachedCoverEntity, CachedMetadataEntity, LyricsCacheDao, LyricsCacheEntity

### Community 46 - "Community 46"
Cohesion: 0.13
Nodes (4): LrclibResponseDto, LrclibLyricsProvider, LrclibMatcher, LrclibMatcherTest

### Community 47 - "Community 47"
Cohesion: 0.13
Nodes (12): computeEdgeFadeAlpha(), computeLyricsAnchorPx(), computeLyricsBottomPadding(), computeLyricsScrollDelta(), computeLyricsScrollOffset(), resolveReturnScrollAction(), ScrollAction, ANIMATE (+4 more)

### Community 50 - "Community 50"
Cohesion: 0.13
Nodes (5): RadioMemory, RadioProgrammer, RadioTrigger, RadioProgrammerTest, track()

### Community 52 - "Community 52"
Cohesion: 0.14
Nodes (5): Collage, Server, VusicColorMath, LyricsMenuContrastTest, VusicDesignTest

### Community 53 - "Community 53"
Cohesion: 0.06
Nodes (30): name, runs-on, steps, timeout-minutes, concurrency, cancel-in-progress, group, jobs (+22 more)

### Community 54 - "Community 54"
Cohesion: 0.11
Nodes (3): ActionListAppWidget, ActionListAppWidgetAppWidgetReceiver, FakeActionListDataRepository

### Community 55 - "Community 55"
Cohesion: 0.12
Nodes (11): LongTextLayout(), LongTextLayoutData, LongTextLayoutPreview(), LongTextLayoutSize, Normal, XSmall, maybeClickable(), showTitle() (+3 more)

### Community 57 - "Community 57"
Cohesion: 0.13
Nodes (4): AvatarFallback, Entry, LruSizer, M6PartATest

### Community 58 - "Community 58"
Cohesion: 0.10
Nodes (9): DownloadStateMachine, DownloadStatus, COMPLETED, DOWNLOADING, FAILED, PAUSED, QUEUED, DownloadStateMachineTest (+1 more)

### Community 59 - "Community 59"
Cohesion: 0.11
Nodes (8): LibraryTab, ALBUMS, ARTISTS, DOWNLOADS, FAVORITES, PLAYLISTS, SONGS, LibraryViewModel

### Community 60 - "Community 60"
Cohesion: 0.13
Nodes (3): Routes, NavRules, NavRulesTest

### Community 61 - "Community 61"
Cohesion: 0.10
Nodes (7): SearchFilter, ALBUMS, ALL, ARTISTS, PLAYLISTS, SONGS, SearchViewModel

### Community 64 - "Community 64"
Cohesion: 0.23
Nodes (12): toDomain(), Album, AlbumDetail, Artist, ArtistDetail, ArtistInfo, Genre, Playlist (+4 more)

### Community 67 - "Community 67"
Cohesion: 0.11
Nodes (11): PendingActionDao, PendingActionEntity, OfflineSyncStore, PendingActionType, SCROBBLE, STAR, STAR_ALBUM, STAR_ARTIST (+3 more)

### Community 68 - "Community 68"
Cohesion: 0.13
Nodes (9): EditLyricsDialog(), formatLyricsForCopy(), formatLyricsForEdit(), formatToLrc(), LyricsMenuSheet(), LyricsOffsetDialog(), MenuSwitchRow(), parseTextSizeToPercent() (+1 more)

### Community 70 - "Community 70"
Cohesion: 0.24
Nodes (5): Beside, NextLineEnd, Placement, RowLayout, RowLayoutTest

### Community 71 - "Community 71"
Cohesion: 0.15
Nodes (8): LyricsCandidate, Instrumental, Loading, LyricsUi, LyricsViewModel, None, Plain, Synced

### Community 73 - "Community 73"
Cohesion: 0.18
Nodes (7): DisplayModeInfo, RefreshRate, RefreshRateMode, AUTO, MAX, STANDARD, RefreshRateTest

### Community 74 - "Community 74"
Cohesion: 0.18
Nodes (3): TrackMediaInfo, TrackMediaItems, TrackMediaItemsTest

### Community 75 - "Community 75"
Cohesion: 0.17
Nodes (3): ReplayGain, CacheSizePolicyTest, ReplayGainTest

### Community 76 - "Community 76"
Cohesion: 0.14
Nodes (10): KrcLyricsParser, LyricsSource, BETTER_LYRICS, CACHE, FILE_TAGS, KUGOU, LRCLIB, PAXSENIX (+2 more)

### Community 77 - "Community 77"
Cohesion: 0.19
Nodes (16): create_workflow(), default_root(), finish_locked(), finish_workflow(), finished_path(), lock_path(), main(), managed_root() (+8 more)

### Community 78 - "Community 78"
Cohesion: 0.09
Nodes (3): GradleRunHeartbeatTest, GradleRunWindowsProcessTest, ProcessAssertionTest

### Community 82 - "Community 82"
Cohesion: 0.09
Nodes (8): LayoutMode, GRID, LIST, Settings, ThemeMode, DARK, LIGHT, SYSTEM

### Community 83 - "Community 83"
Cohesion: 0.18
Nodes (3): LoginErrorMapper, LoginFailure, LoginErrorMapperTest

### Community 85 - "Community 85"
Cohesion: 0.14
Nodes (10): NoDataContent(), VerticalListItem(), PillShapedButton(), RectangularIconButton(), Action, CLEAR_QUEUE, NEXT, NONE (+2 more)

### Community 86 - "Community 86"
Cohesion: 0.16
Nodes (8): NetworkClass, MOBILE, OTHER, WIFI, NetworkMonitor, onAvailable(), onCapabilitiesChanged(), onLost()

### Community 87 - "Community 87"
Cohesion: 0.17
Nodes (4): Track, flowNow(), M45Test, track()

### Community 88 - "Community 88"
Cohesion: 0.25
Nodes (4): Result, Segment, UsageBarMath, UsageBarMathTest

### Community 94 - "Community 94"
Cohesion: 0.18
Nodes (3): PersistedTrack, QueueSnapshot, QueueStore

### Community 95 - "Community 95"
Cohesion: 0.10
Nodes (6): Failed, Idle, LibraryCounts, Ok, Testing, TestState

### Community 96 - "Community 96"
Cohesion: 0.23
Nodes (6): album(), artist(), LibraryOrderingTest, playlist(), SearchGroupingTest, track()

### Community 98 - "Community 98"
Cohesion: 0.16
Nodes (3): ExpressiveToolbarAppWidget, ExpressiveToolbarAppWidgetReceiver, ActionUtils

### Community 99 - "Community 99"
Cohesion: 0.26
Nodes (3): CachePolicy, CachePolicyTest, FakeClock

### Community 101 - "Community 101"
Cohesion: 0.25
Nodes (3): QueueHighlight, State, QueueHighlightTest

### Community 103 - "Community 103"
Cohesion: 0.18
Nodes (3): Credentials, CredentialsStore, HostRewriteInterceptor

### Community 107 - "Community 107"
Cohesion: 0.20
Nodes (8): ActiveRow(), DoneRow(), DownloadsScreen(), ProgressThumb(), SectionLabel(), StorageRow(), DownloadRow, TopBarSearch

### Community 109 - "Community 109"
Cohesion: 0.15
Nodes (5): VLog, EventListener, Listener, UpstreamLogger, DataSource

### Community 110 - "Community 110"
Cohesion: 0.25
Nodes (3): LrcParser, Tag, LrcParserTest

### Community 112 - "Community 112"
Cohesion: 0.22
Nodes (7): KugouCandidate, KugouCandidatesResponse, KugouDownloadResponse, KuGouLyricsProvider, KugouSearchData, KugouSearchResponse, KugouSearchSong

### Community 115 - "Community 115"
Cohesion: 0.21
Nodes (3): ImageGridAppWidget, ImageGridAppWidgetReceiver, WidgetContent()

### Community 118 - "Community 118"
Cohesion: 0.20
Nodes (3): QualityPolicy, StreamQuality, QualityPolicyTest

### Community 121 - "Community 121"
Cohesion: 0.12
Nodes (16): description, label, options, placeholder, render, body, attributes, id (+8 more)

### Community 125 - "Community 125"
Cohesion: 0.21
Nodes (4): LibrarySorts, Option, SortChoice, LibrarySortTest

### Community 138 - "Community 138"
Cohesion: 0.27
Nodes (5): Partition, PlaylistDuplicates, PendingDupCheck, PlaylistDuplicatesTest, track()

### Community 153 - "Community 153"
Cohesion: 0.41
Nodes (3): FakeCoverStore, FakeDownload, OfflineCoverStoreTest

### Community 160 - "Community 160"
Cohesion: 0.33
Nodes (3): migrateStoredTheme(), resolveDark(), ThemeModeTest

### Community 161 - "Community 161"
Cohesion: 0.42
Nodes (3): ClearDownloadQueueTest, FakeDownload, FakeDownloadQueueManager

### Community 164 - "Community 164"
Cohesion: 0.24
Nodes (5): Kind, LOCAL, STREAM, PlaybackSource, PlaybackSourceTest

### Community 168 - "Community 168"
Cohesion: 0.60
Nodes (5): HeaderTwoRowGrid(), SideBarTwoRowGrid(), SpacedColumn(), SpacedRow(), TwoRowGrid()

### Community 169 - "Community 169"
Cohesion: 0.29
Nodes (4): Album, Artist, SearchGrouping, TopHit

### Community 170 - "Community 170"
Cohesion: 0.27
Nodes (3): CachedMembership, StableCacheKey, StableCacheKeyTest

### Community 179 - "Community 179"
Cohesion: 0.25
Nodes (8): SettingsRoute, ABOUT, APPEARANCE, CONNECTIVITY, HOME, SERVER, SOUND, STORAGE

### Community 182 - "Community 182"
Cohesion: 0.33
Nodes (3): emit_heartbeat(), terminate_child(), wait_for_child()

### Community 185 - "Community 185"
Cohesion: 0.40
Nodes (4): interface, default_prompt, display_name, short_description

### Community 186 - "Community 186"
Cohesion: 0.40
Nodes (4): ReplayGainMode, ALBUM, OFF, TRACK

## Knowledge Gaps
- **102 isolated node(s):** `PassToList`, `None`, `TracksAdded`, `FULL`, `MEDIUM` (+97 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 840 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **101 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SettingsStore` connect `Community 14` to `Community 0`, `Community 3`, `Community 4`, `Community 8`, `Community 9`, `Community 140`, `Community 12`, `Community 15`, `Community 16`, `Community 18`, `Community 19`, `Community 23`, `Community 152`, `Community 25`, `Community 31`, `Community 160`, `Community 34`, `Community 43`, `Community 174`, `Community 59`, `Community 61`, `Community 66`, `Community 68`, `Community 69`, `Community 71`, `Community 79`, `Community 81`, `Community 82`, `Community 87`, `Community 91`, `Community 95`, `Community 97`, `Community 104`, `Community 117`, `Community 125`?**
  _High betweenness centrality (0.118) - this node is a cross-community bridge._
- **What connects `PassToList`, `None`, `TracksAdded` to the rest of the system?**
  _102 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.048101673101673105 - nodes in this community are weakly interconnected._
- **Why does `MusicDataRepository` connect `Community 38` to `Community 0`, `Community 3`, `Community 8`, `Community 9`, `Community 10`, `Community 12`, `Community 13`, `Community 15`, `Community 16`, `Community 19`, `Community 20`, `Community 26`, `Community 31`, `Community 33`, `Community 48`, `Community 177`, `Community 59`, `Community 61`, `Community 64`, `Community 66`, `Community 67`, `Community 95`, `Community 104`, `Community 107`, `Community 117`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.05765870704717531 - nodes in this community are weakly interconnected._
- **Why does `Track` connect `Community 87` to `Community 128`, `Community 0`, `Community 129`, `Community 5`, `Community 136`, `Community 9`, `Community 138`, `Community 11`, `Community 8`, `Community 13`, `Community 10`, `Community 15`, `Community 12`, `Community 19`, `Community 20`, `Community 22`, `Community 154`, `Community 155`, `Community 157`, `Community 33`, `Community 162`, `Community 166`, `Community 41`, `Community 43`, `Community 48`, `Community 50`, `Community 59`, `Community 61`, `Community 64`, `Community 68`, `Community 71`, `Community 74`, `Community 81`, `Community 91`, `Community 92`, `Community 94`, `Community 96`, `Community 104`, `Community 117`, `Community 118`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.05002337540906966 - nodes in this community are weakly interconnected._