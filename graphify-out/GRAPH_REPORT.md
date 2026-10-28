# Graph Report - Feishin  (2026-10-28)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 5979 nodes · 16780 edges · 244 communities (136 shown, 108 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 471 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e4062a92`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- NowPlayingScreen.kt
- .agents/skills/glance-canonical-layouts/references/text/layout/TextWithImageLayout.kt
- Models.kt
- Contrast
- dispatchers
- FakeMusicRepo
- Test
- MainActivity.kt
- Lyrics
- assertequals
- .agent/skills/glance-canonical-layouts/references/collections/CheckListAppWidget.kt
- .create_workflow
- .agents/skills/glance-canonical-layouts/references/toolbars/layout/ExpressiveToolbarLayout.kt
- Composable
- HomeViewModel.kt
- PlaylistEvents
- SettingsStore
- .agents/skills/glance-canonical-layouts/references/text/layout/LongTextLayout.kt
- inject
- DownloadRepository
- PlaylistOrderingTest
- PlayerManager
- .agent/skills/glance-canonical-layouts/references/CanonicalLayoutActivity.kt
- Nav.kt
- BoundedQueuePlayer
- SettingsViewModel
- Composable
- MusicRepository
- RoundedCornerShape
- .agents/skills/glance-canonical-layouts/references/text/layout/FullBleedImageLayout.kt
- MainScaffold
- .create_workflow
- .agents/skills/gradle-run/scripts/gradle_run.py
- Test
- MusicDataRepository
- Track
- RadioFakeRepo
- LibraryViewModel
- .agent/skills/gradle-run/scripts/gradle_run.py
- UiState
- LrclibLyricsProvider
- .agent/skills/gradle-run/scripts/test_gradle_run.py
- Composable
- WidgetActionReceiver.kt
- Vusic
- DownloadWorker.kt
- EmbeddedLyricsExtractor
- IconButton
- PlayerService.kt
- QueueHandoffTest
- Query
- ArtistScreen
- Vusic Android Music Client
- QueueOpsTest
- CachedTrackStore
- DeviceFilesViewModel.kt
- Action
- Jetpack Compose Performance Skill
- Test
- MusicDatabase.kt
- Test
- .run_gradle
- SubsonicAuth
- .select
- SearchViewModel
- FavFakeRepo
- Test
- Romanizer
- NowPlayingScreen
- DownloadStatus
- M55LogicTest.kt
- KuGouLyricsProvider.kt
- app Gradle module
- Test
- LyricsViewModel.kt
- WidgetUpdater.kt
- LyricsTabCorrectionsTest
- mutablestateflow
- MediaItem
- Test
- PrefetchManager.kt
- OfflineSyncStore
- .childrenOf
- .decide
- .agents/skills/glance-canonical-layouts/references/collections/data/FakeCheckListDataRepository.kt
- Test
- DownloadScheduler.kt
- .agent/skills/glance-canonical-layouts/references/collections/ActionListAppWidget.kt
- CacheSizePolicy
- .toTrack
- .map
- StorageScreen
- NavidromeLyricsProvider
- FakeImageGridDataRepository
- .agent/skills/glance-canonical-layouts/references/collections/layout/RoundedScrollingLazyColumn.kt
- Path
- NetworkMonitor
- ReplayGainTest
- MusicDatabase
- FeishinApp.kt
- FakeImageTextListDataRepository
- FakeTextWithImageRepository
- Test
- DownloadsScreen
- Test
- .compute
- SyncedLyrics
- PlaylistAddSpeedTest
- LibraryScreen
- ScrobblePolicy
- .agents/skills/glance-canonical-layouts/references/utils/ImageUtils.kt
- CachePolicy
- PlaybackProgress
- .state
- FakeActionListDataRepository
- Test
- .parse
- LocalAudioCoverFetcher.kt
- LiveLruCacheEvictor
- PlayerModule.kt
- DownloadsViewModel
- LyricsMatchingTest
- QueueStoreTest
- build.yml
- PlayerManager
- FakeLongTextRepository
- Vusic (Feishin Mobile)
- QualityPolicy
- .results
- CredentialsStore
- QueueStore.kt
- TrackMediaItemsTest
- .map
- attributes
- Phase 8.3 Status (1.1.3)
- .agents/skills/glance-canonical-layouts/references/utils/FontUtils.kt
- SettingsViewModel.kt
- RomanizerTest
- Vusic (Feishin) Project Instructions & Mandatory Skills
- .albumsToTracks
- SortChoice
- Test
- MetadataCache
- DynamicTrustManager
- NowPlayingWidget.kt
- data layer (Retrofit Subsonic API, stores, Room, repositories)
- PlayerManager singleton
- ui layer (Compose NavHost, screens, viewmodels, components)
- P9-B: Device Files
- Compose component design skill
- DeviceFilesFilter
- .chunks
- .partition
- .d
- DownloadModule.kt
- NetworkModule.kt
- Popen
- .build
- .of
- LyricsPickTest
- AuthRepository.kt
- SettingsStore.kt
- LoginViewModel.kt
- FakeCoverStore
- DtoParsingTest
- with
- .parse
- .provideOkHttp
- Lyrics provider chain
- Phase 8.4 Status (1.1.4)
- MessageHost
- PassThroughOverscroll.kt
- PlaybackSource
- ProgressThrottle
- .build
- FakeDownloadQueueManager
- QueueStoreShuffleTest
- Android Permissions and IPC Security Skill
- QueueShufflePipelineTest.kt
- .shuffledTail
- LayoutMode
- WidgetPush.kt
- QueueStoreRadioTest
- Project Assumptions and Verification Log
- .redact
- TopLevelTitleBar.kt
- Gradle Run Skill
- RepoModule.kt
- CoilBitmapLoader
- SettingsRoute
- ConcurrentDownloadsTest
- AspectRatio
- LyricsQueueStripTest
- Lyrics menu
- extract_diagnostics
- StorageLimits
- AuthInterceptor
- HostRewriteInterceptor.kt
- TextWithImageLayoutSize
- interface
- WorkflowBusy
- graphify
- TextWithImageLayoutSize
- interface
- RedactingHttpLogger
- RadioFetcher.kt
- .startScan
- LyricsRepository
- Error Handling Patterns skill
- DeviceFilesFilter
- DisplayListener
- Online lyrics providers (BetterLyrics, LRCLIB, KuGou)
- DarkToneSpec.kt
- android-cli skill
- Models.kt (DTOs)
- RedactingHttpLogger
- SettingsStore (DataStore)
- token-efficiency skill

## God Nodes (most connected - your core abstractions)
1. `SettingsStore` - 142 edges
2. `PlayerManager` - 122 edges
3. `MusicDataRepository` - 121 edges
4. `SettingsViewModel` - 106 edges
5. `Track` - 86 edges
6. `Lyrics` - 83 edges
7. `DownloadRepository` - 69 edges
8. `Action` - 59 edges
9. `RoundedCornerShape` - 56 edges
10. `FakeMusicRepo` - 54 edges

## Surprising Connections (you probably didn't know these)
- `CheckListItem()` --calls--> `CircleIconButton()`  [INFERRED]
  .agent/skills/glance-canonical-layouts/references/collections/layout/CheckListLayout.kt → app/src/main/java/com/feishin/mobile/ui/detail/DetailScreens.kt
- `ArtistAvatar()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/ArtistAvatar.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `MiniPlayer()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/MiniPlayer.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `PlaylistCoverArt()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/PlaylistArt.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt
- `ActionTile()` --calls--> `RoundedCornerShape`  [INFERRED]
  app/src/main/java/com/feishin/mobile/ui/components/TrackActionsSheet.kt → .agents/skills/glance-canonical-layouts/references/toolbars/layout/Buttons.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Device files flow (permission → MediaStore query → filter → local playback)** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_androidmanifest, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_devicefilesviewmodel, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_devicefilesfilter, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_devicefilesscreen, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_track, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_queuestore, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_playermanager [EXTRACTED 0.85]
- **Library cache-first rendering flow** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_musicrepository, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_metadatacache, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_libraryrefresh, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_cachepolicy, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_libraryscreen [EXTRACTED 0.85]
- **Online lyrics lookup with privacy-preserving payload** — readme_lyrics_providers, readme_kuromoji, readme_paxsenix, readme_vusic [EXTRACTED 0.85]
- **Phase 7 runtime performance tuning** — plan_collectasstatewithlifecycle, plan_baseline_profile, plan_strictmode, plan_encrypted_shared_prefs, plan_feishinapp, plan_perf_after_doc [EXTRACTED 0.85]
- **Phase 8 gesture correctness work** — plan_sheet_physics, plan_miniplayer_gesture, plan_miniplayer_drag_clear, plan_queue_redesign, plan_offline_banner [EXTRACTED 0.85]
- **Android Auto browse-to-playback flow** — plan_playerservice, plan_android_auto_browse_tree, plan_default_session_and_library_commands, plan_adopt_external_queue, plan_playermanager [EXTRACTED 0.90]
- **App data layer (Room, DataStore, EncryptedSharedPreferences, Retrofit)** — readme_room_database, readme_datastore, readme_encryptedsharedpreferences, readme_subsonicapi, readme_hilt [EXTRACTED 0.90]
- **Artwork-derived color scheme with enforced contrast** — plan_playing_scheme, plan_dark_tone_spec, plan_contrast, plan_vusic_colors, plan_mini_player [EXTRACTED 0.90]
- **Device Files Flow (permission -> MediaStore -> playback -> lyrics)** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_p9b_device_files, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_read_media_audio, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_mediastore, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_view_my_sheet, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_playerservice, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_filetagsprovider [EXTRACTED 0.90]
- **Lyrics resolution flow (provider → selection → cache → Now Playing)** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_navidromelyricsprovider, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyricsselection, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lrcparser, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyricsviewmodel, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_musicdatabase, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_nowplayingscreen [EXTRACTED 0.90]
- **Lyrics Tab Rendering and Scroll Sync** — plan_lyricsscrollengine, plan_lyricstabcorrections, plan_romanizer, plan_lyricsmenusheet, plan_playermanager [EXTRACTED 0.90]
- **Lyrics UI and Sync (parser, selection, sync engine, settings)** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lrcparser, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyricsselection, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyrics_sync_engine, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyrics_settings, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_playermanager [EXTRACTED 0.90]
- **Queue Persistence and Restore Flow** — plan_playermanager, plan_queuestore, plan_playerservice, plan_datastore, plan_queueops [EXTRACTED 0.90]
- **Gradle Run Workflow Lifecycle** — agent_skills_gradle_run_skill_gradle_run, agent_skills_gradle_run_skill_gradle_run_script, agent_skills_gradle_run_skill_workflow_lifecycle, agent_skills_gradle_run_skill_status_uncertainty, agent_skills_gradle_run_skill_failure_fingerprint [EXTRACTED 0.95]
- **Lyrics Pipeline Flow (cache -> LRCLIB -> metadata -> upgrade)** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyricsrepository, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lrclibprovider, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_navidromelyricsprovider, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_filetagsprovider, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyrics_cache, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_lyrics_pipeline [EXTRACTED 0.95]
- **Lyrics Provider Lookup Chain** — plan_lyricsrepository, plan_betterlyricsprovider, plan_lrcliblyricsprovider, plan_kugoulyricsprovider, plan_lrclibmatcher, plan_lrclibclient [EXTRACTED 0.95]
- **Lyrics provider lookup chain** — changelog_provider_chain, changelog_betterlyrics, changelog_lrclib, changelog_paxsenix, changelog_kugou, changelog_opensubsonic_lyrics, changelog_lyrics_cache, changelog_lyrics_reliability [EXTRACTED 0.95]
- **Shuffle snapshot/restore flow** — plan_player_manager, plan_shuffle_plan, plan_queue_ops, plan_queue_store [EXTRACTED 0.95]
- **Single Media3 player shared by all surfaces** — readme_playerservice, readme_mediasession, readme_playermanager, readme_glance_widget, readme_android_auto_service, readme_exoplayer [EXTRACTED 0.95]
- **Always-active mandatory project skills** — gemini_jetpack_compose_performance_skill, gemini_compose_performance_skill, gemini_compose_state_and_effects_skill, gemini_compose_animations_skill, gemini_compose_component_design_skill, gemini_compose_focus_navigation_skill, gemini_edge_to_edge_skill, gemini_glance_canonical_layouts_skill, gemini_emil_design_eng_skill, gemini_kotlin_concurrency_and_flow_skill, gemini_media3_cast_integration_skill, gemini_error_handling_patterns_skill, gemini_token_efficiency_skill [EXTRACTED 1.00]
- **Jetpack Compose skill family** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_compose_component_design, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_compose_focus_navigation, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_compose_ui_testing_patterns, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_compose_animations, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_compose_state_and_effects [INFERRED 0.80]
- **Viewport Visibility Tracking Pattern** — agent_skills_jetpack_compose_performance_references_layouts_on_visibility_changed, agent_skills_jetpack_compose_performance_references_animation_shared_transition_deferral, agent_skills_jetpack_compose_performance_references_effects_and_threading_side_effect_api_guide, agent_skills_jetpack_compose_performance_skill_lazy_layout_scope_guard [INFERRED 0.80]
- **Download and offline pipeline** — changelog_offline_cover_art, changelog_simultaneous_downloads, changelog_clear_download_queue, changelog_download_auto_resume, changelog_mobile_data_policy, changelog_mobile_data_switches, changelog_song_cache_limit, changelog_stream_cache [INFERRED 0.80]
- **Error resilience patterns** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_circuit_breaker, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_error_collector, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_retry_decorator, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_with_fallback [INFERRED 0.80]
- **Android IPC caller authentication and permission enforcement flow** — session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_caller_verifier, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_secure_bound_service, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_secure_data_provider, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_protected_receiver, session_cfe500a5_af3b_4283_9511_bdf627ca4ca2_uri_permission_manager [INFERRED 0.85]
- **Compose Phase Deferral Pattern** — agent_skills_jetpack_compose_performance_skill_phase_deferral, agent_skills_jetpack_compose_performance_references_animation_animation, agent_skills_jetpack_compose_performance_references_layouts_layouts, agent_skills_jetpack_compose_performance_references_graphics_performance_draw_with_cache [INFERRED 0.85]
- **Lyrics tab UI experience** — changelog_lyrics_display, changelog_fluid_lyrics_animation, changelog_lyrics_edge_fade, changelog_lyrics_tab_sync, changelog_queue_bottom_strip, changelog_lyrics_menu, changelog_lyrics_line_gap, changelog_now_playing_screen [INFERRED 0.85]

## Communities (244 total, 108 thin omitted)

### Community 0 - "NowPlayingScreen.kt"
Cohesion: 0.03
Nodes (10): Favorites, Placeholder, PlaylistCover, DownloadIndicator(), ActionTarget, SortOption, RowText(), SettingsCardScope (+2 more)

### Community 1 - ".agents/skills/glance-canonical-layouts/references/text/layout/TextWithImageLayout.kt"
Cohesion: 0.04
Nodes (40): CheckListLayoutDimensions, CheckListLayoutTextStyles, Grid(), GridItem(), ImageGridLayout(), ImageGridLayoutDimensions, ImageGridLayoutSize, Large (+32 more)

### Community 2 - "Models.kt"
Cohesion: 0.06
Nodes (48): AlbumDetailDto, AlbumDto, AlbumDtoListSerializer, AlbumList2Dto, AlbumListDto, ArtistDetailDto, ArtistDto, ArtistDtoListSerializer (+40 more)

### Community 3 - "Contrast"
Cohesion: 0.06
Nodes (15): Contrast, Hsl, NowPlayingColors, NowPlayingPalette, PlayingColors, PlayingScheme, animatedPlayingScheme(), PlayingSchemeCache (+7 more)

### Community 4 - "dispatchers"
Cohesion: 0.06
Nodes (18): ImageGridAppWidget, ImageGridAppWidgetReceiver, WidgetContent(), ImageTextListAppWidget, ImageTextListAppWidgetReceiver, FullBleedImageAppWidget, FullBleedImageAppWidgetReceiver, TextWithImageAppWidget (+10 more)

### Community 5 - "FakeMusicRepo"
Cohesion: 0.05
Nodes (6): album(), FakeCacheBytes, FakeCachedTrackDao, FakeMusicRepo, track(), ViewModelStateTest

### Community 6 - "Test"
Cohesion: 0.06
Nodes (5): MiniPlayerGesture, SheetPhysics, MiniPlayerGestureTest, SheetPhysicsTest, LyricsAnimationTest

### Community 7 - "MainActivity.kt"
Cohesion: 0.05
Nodes (14): LayoutSamplesTheme(), LayoutSamplesTheme(), DisplayModeInfo, RefreshRate, RefreshRateMode, AUTO, MAX, STANDARD (+6 more)

### Community 8 - "Lyrics"
Cohesion: 0.06
Nodes (26): DebugLyricsFixtures, fromSynced(), LyricLine, Lyrics, LyricsKind, INSTRUMENTAL, NONE, PLAIN (+18 more)

### Community 10 - ".agent/skills/glance-canonical-layouts/references/collections/CheckListAppWidget.kt"
Cohesion: 0.06
Nodes (38): CheckListAppWidget, CheckListAppWidgetReceiver, ActionListLayout(), Content(), GridView(), ListView(), CheckListItem(), CheckListLayout() (+30 more)

### Community 11 - ".create_workflow"
Cohesion: 0.08
Nodes (4): GradleRunProcessTest, GradleRunStatusTest, GradleRunTestCase, GradleRunWorkflowTest

### Community 12 - ".agents/skills/glance-canonical-layouts/references/toolbars/layout/ExpressiveToolbarLayout.kt"
Cohesion: 0.09
Nodes (33): ExpressiveToolbarAppWidget, ExpressiveToolbarAppWidgetReceiver, AllButtonsScaledLayout(), CenterButton(), CenterButtonOnlyLayout(), checkCornerButtonsListSize(), CornerButton(), CornerButtonsGrid() (+25 more)

### Community 13 - "Composable"
Cohesion: 0.08
Nodes (37): HeaderTwoRowGrid(), SideBarTwoRowGrid(), SpacedColumn(), SpacedRow(), TwoRowGrid(), SearchBar(), SearchIconButton(), SearchToolBarButton (+29 more)

### Community 14 - "HomeViewModel.kt"
Cohesion: 0.06
Nodes (14): CacheModule, AlbumSnap, DiskHomeCache, HomeCache, HomeSnapshot, InMemoryHomeCache, TrackSnap, AlbumTarget (+6 more)

### Community 15 - "PlaylistEvents"
Cohesion: 0.09
Nodes (29): FavoritesChanged, FavoritesStore, PlaylistEvent, PlaylistEvents, TracksAdded, speedDialKey(), ActionTile(), ActionTiles() (+21 more)

### Community 17 - ".agents/skills/glance-canonical-layouts/references/text/layout/LongTextLayout.kt"
Cohesion: 0.08
Nodes (26): LongTextLayout(), LongTextLayoutData, LongTextLayoutPreview(), LongTextLayoutSize, Normal, XSmall, maybeClickable(), showTitle() (+18 more)

### Community 18 - "inject"
Cohesion: 0.07
Nodes (9): BetterLyricsProvider, BetterLyricsResponse, FileTagLyricsProvider, PaxsenixLyricsProvider, LyricsRepository, LyricsProvider, Hit, LyricsProbeTest (+1 more)

### Community 19 - "DownloadRepository"
Cohesion: 0.06
Nodes (5): removeIf(), DownloadGroupEntity, DownloadProgress, DownloadRepository, observeAllSnapshot()

### Community 20 - "PlaylistOrderingTest"
Cohesion: 0.09
Nodes (9): PlaylistOrdering, PlaylistSort, PlaylistSortKey, ARTIST, DATE_ADDED, DURATION, PLAYLIST_ORDER, TITLE (+1 more)

### Community 21 - "PlayerManager"
Cohesion: 0.10
Nodes (3): ClearedQueue, PlayerManager, QueueItem

### Community 22 - ".agent/skills/glance-canonical-layouts/references/CanonicalLayoutActivity.kt"
Cohesion: 0.08
Nodes (12): CanonicalLayoutActivity, CanonicalLayoutRow(), CanonicalLayoutRowData, Content(), Header(), requestPin(), CanonicalLayoutActivity, CanonicalLayoutRow() (+4 more)

### Community 23 - "Nav.kt"
Cohesion: 0.05
Nodes (5): OfflineBanner(), AuthRoutes, FeishinTheme(), VusicControlColors, VusicControlDefaults

### Community 24 - "BoundedQueuePlayer"
Cohesion: 0.08
Nodes (6): BoundedQueuePlayer, Timeline, BoundedQueuePlayerTest, FakeTimeline, Fake, QueueTapToggleTest

### Community 26 - "Composable"
Cohesion: 0.10
Nodes (24): FluidContentIconButton(), FluidHeaderAppIcon(), FluidHeaderIconButton(), Header(), ToolBarLayout(), ToolBarLayoutSize, HeaderTwoRowGrid, HorizontalRow (+16 more)

### Community 28 - "RoundedCornerShape"
Cohesion: 0.10
Nodes (31): RoundedCornerShape, FULL, MEDIUM, AlbumCard(), ArtistCard(), ClickableName(), CoverImage(), EqualizerOverlay() (+23 more)

### Community 29 - ".agents/skills/glance-canonical-layouts/references/text/layout/FullBleedImageLayout.kt"
Cohesion: 0.10
Nodes (16): ActionDemonstrationActivity, EmptyListContent(), ImageGridItemData, NoDataContent(), FullBleedImageLayout(), FullBleedImageLayoutPreview(), GalleryItemCard(), SnapScrollingGallery() (+8 more)

### Community 30 - "MainScaffold"
Cohesion: 0.07
Nodes (11): MiniPlayer(), MiniPlayerRing(), SongPagerSection(), MainScaffold(), SegmentedControl(), SettingRowText(), SettingsActionButton(), SettingsControlBlock() (+3 more)

### Community 31 - ".create_workflow"
Cohesion: 0.10
Nodes (4): GradleRunProcessTest, interrupt_during_finalization(), GradleRunTestCase, GradleRunWorkflowTest

### Community 32 - ".agents/skills/gradle-run/scripts/gradle_run.py"
Cohesion: 0.10
Nodes (27): add_fingerprint(), append_run(), bounded_summary(), Diagnostics, display_command(), effective_command(), emit_summary(), extract_diagnostics() (+19 more)

### Community 33 - "Test"
Cohesion: 0.10
Nodes (8): computeScrollDelta(), computeScrollDurationMs(), computeScrollOffset(), evaluateRetargetDelta(), LyricsDisplayTokens, shouldAutoScroll(), LyricsLineGapTest, LyricsScrollEngineTest

### Community 34 - "MusicDataRepository"
Cohesion: 0.09
Nodes (4): MusicDataRepository, ArtistPhoto(), ArtistPhotoCard(), ArtistPhotoRow()

### Community 35 - "Track"
Cohesion: 0.16
Nodes (14): toDomain(), Album, AlbumDetail, Artist, ArtistDetail, ArtistInfo, Genre, Playlist (+6 more)

### Community 36 - "RadioFakeRepo"
Cohesion: 0.09
Nodes (4): RadioFetcher, RadioFakeRepo, RadioFetcherTest, track()

### Community 37 - "LibraryViewModel"
Cohesion: 0.09
Nodes (9): tabLabel(), LibraryTab, ALBUMS, ARTISTS, DOWNLOADS, FAVORITES, PLAYLISTS, SONGS (+1 more)

### Community 38 - ".agent/skills/gradle-run/scripts/gradle_run.py"
Cohesion: 0.13
Nodes (34): append_run(), bounded_summary(), create_workflow(), display_command(), effective_command(), emit_summary(), finish_locked(), finish_workflow() (+26 more)

### Community 39 - "UiState"
Cohesion: 0.07
Nodes (12): Data, Empty, Err, Error, Loading, Ok, Result, UiState (+4 more)

### Community 40 - "LrclibLyricsProvider"
Cohesion: 0.10
Nodes (4): LrclibResponseDto, LrclibLyricsProvider, LrclibMatcher, LrclibMatcherTest

### Community 41 - ".agent/skills/gradle-run/scripts/test_gradle_run.py"
Cohesion: 0.07
Nodes (8): GradleRunHeartbeatTest, GradleRunWindowsProcessTest, process_is_running(), ProcessAssertionTest, GradleRunHeartbeatTest, GradleRunWindowsProcessTest, process_is_running(), ProcessAssertionTest

### Community 42 - "Composable"
Cohesion: 0.12
Nodes (21): HorizontalContent(), Image(), maybeClickable(), NoDataContent(), TextStack(), TextWithImageData, TextWithImageLayout(), TextWithImageLayoutDimensions (+13 more)

### Community 43 - "WidgetActionReceiver.kt"
Cohesion: 0.11
Nodes (4): AutoBrowseDeviceTest, BrowserListener, CrashGuard, WidgetActionReceiver

### Community 44 - "Vusic"
Cohesion: 0.09
Nodes (38): Accessibility, Add to playlist, Android Auto, Clear all download queue, Volume-ramp crossfade, Device Files in Library, Download auto-resume after reconnect, Dynamic Theme (+30 more)

### Community 45 - "DownloadWorker.kt"
Cohesion: 0.09
Nodes (7): DownloadNotifications, DownloadWorker, Outcome, DONE, FAILED, PAUSED, RETRY

### Community 46 - "EmbeddedLyricsExtractor"
Cohesion: 0.09
Nodes (3): EmbeddedLyricsExtractor, EmbeddedLyricsExtractorTest, FilterInputStream

### Community 47 - "IconButton"
Cohesion: 0.11
Nodes (22): IconButton(), ArtistAvatar(), rememberSavableListState(), MessageHost(), LoginMessageCard(), LoginScreen(), AppNav(), EditLyricsDialog() (+14 more)

### Community 49 - "QueueHandoffTest"
Cohesion: 0.14
Nodes (6): ListHandoff, PassToList, QueueHandoff, TakeBySheet, NestedScrollConnection, QueueHandoffTest

### Community 50 - "Query"
Cohesion: 0.12
Nodes (3): DownloadDao, DownloadEntity, DownloadGroupDao

### Community 51 - "ArtistScreen"
Cohesion: 0.08
Nodes (16): rememberSavableGridState(), ArtistCollapsedTopBar(), ArtistControls(), ArtistHero(), ArtistScreen(), CircleIconButtonShim(), AlbumDetailScreen(), AlbumHeader() (+8 more)

### Community 52 - "Vusic Android Music Client"
Cohesion: 0.09
Nodes (35): ABI splits (arm64-v8a + x86_64) and resConfigs("en"), PlayerManager.adoptExternalQueue, Android Auto browse tree (4 root tabs), App logo (eighth note) and ic_stat_vusic, Hand-written baseline-prof.txt + :baselineprofile module, CHANGELOG 1.0.0, collectAsStateWithLifecycle migration (94 sites / 17 files), DEFAULT_SESSION_AND_LIBRARY_COMMANDS fix (+27 more)

### Community 53 - "QueueOpsTest"
Cohesion: 0.15
Nodes (3): QueueOps, Entry, QueueOpsTest

### Community 54 - "CachedTrackStore"
Cohesion: 0.09
Nodes (6): CacheBytes, CachedTrackStore, Media3CacheBytes, toTrack(), CachedTrackDao, CachedTrackEntity

### Community 55 - "DeviceFilesViewModel.kt"
Cohesion: 0.10
Nodes (3): DeviceFilesState, DeviceFilesViewModel, ContentObserver

### Community 56 - "Action"
Cohesion: 0.15
Nodes (17): ListItem(), VerticalListItem(), PillShapedButton(), RectangularIconButton(), RoundedCornerShape, FULL, MEDIUM, ListItem() (+9 more)

### Community 57 - "Jetpack Compose Performance Skill"
Cohesion: 0.09
Nodes (33): Animation and Shared Transitions Reference, Persistent Animatable in Modifier.Node, Defer sharedBounds/sharedElement Until Visible, collectAsStateWithLifecycle, Effect Execution Ordering and Teardown Costs, Effects, Lifecycle, and Threading Reference, Side Effects API Selection Guide, AsyncImage over painterResource (+25 more)

### Community 58 - "Test"
Cohesion: 0.11
Nodes (6): AppNavigationBar(), goToLibraryTab(), goToTab(), Routes, NavRules, NavRulesTest

### Community 59 - "MusicDatabase.kt"
Cohesion: 0.09
Nodes (6): LyricsCacheMigrationTest, CachedCoverDao, CachedCoverEntity, CachedMetadataEntity, LyricsCacheDao, LyricsCacheEntity

### Community 60 - "Test"
Cohesion: 0.13
Nodes (5): RadioMemory, RadioProgrammer, RadioTrigger, RadioProgrammerTest, track()

### Community 63 - ".select"
Cohesion: 0.14
Nodes (5): Collage, Server, VusicColorMath, LyricsMenuContrastTest, VusicDesignTest

### Community 64 - "SearchViewModel"
Cohesion: 0.09
Nodes (9): filterOf(), labelOf(), SearchFilter, ALBUMS, ALL, ARTISTS, PLAYLISTS, SONGS (+1 more)

### Community 66 - "Test"
Cohesion: 0.13
Nodes (4): AvatarFallback, Entry, LruSizer, M6PartATest

### Community 68 - "NowPlayingScreen"
Cohesion: 0.16
Nodes (20): formatDuration(), MarqueeTitle(), parseTextSizeToPercent(), ControlsRow(), dim75(), IntroCountdownDots(), lyricsEdgeFade(), LyricsPanel() (+12 more)

### Community 69 - "DownloadStatus"
Cohesion: 0.11
Nodes (9): DownloadStateMachine, DownloadStatus, COMPLETED, DOWNLOADING, FAILED, PAUSED, QUEUED, DownloadStateMachineTest (+1 more)

### Community 70 - "M55LogicTest.kt"
Cohesion: 0.13
Nodes (6): LibraryOrdering, album(), artist(), LibraryOrderingTest, playlist(), track()

### Community 71 - "KuGouLyricsProvider.kt"
Cohesion: 0.14
Nodes (9): KrcLyricsParser, KugouCandidate, KugouCandidatesResponse, KugouDownloadResponse, KuGouLyricsProvider, KugouSearchData, KugouSearchResponse, KugouSearchSong (+1 more)

### Community 72 - "app Gradle module"
Cohesion: 0.10
Nodes (29): Android Auto service, app Gradle module, baselineprofile Gradle module, CI pipeline, DataStore settings, EncryptedSharedPreferences credential store, ExoPlayer, Feishin (+21 more)

### Community 74 - "LyricsViewModel.kt"
Cohesion: 0.13
Nodes (11): LyricsCandidate, formatLyricsForCopy(), formatLyricsForEdit(), formatToLrc(), Instrumental, Loading, LyricsUi, LyricsViewModel (+3 more)

### Community 75 - "WidgetUpdater.kt"
Cohesion: 0.16
Nodes (4): WidgetState, WidgetStateStore, WidgetExtras, WidgetUpdater

### Community 76 - "LyricsTabCorrectionsTest"
Cohesion: 0.15
Nodes (11): computeEdgeFadeAlpha(), computeLyricsAnchorPx(), computeLyricsBottomPadding(), computeLyricsScrollDelta(), computeLyricsScrollOffset(), resolveReturnScrollAction(), ScrollAction, ANIMATE (+3 more)

### Community 79 - "Test"
Cohesion: 0.14
Nodes (7): CachedMembership, PrefetchWindow, StableCacheKey, CachedMembershipTest, PrefetchWindowTest, Row, StableCacheKeyTest

### Community 81 - "OfflineSyncStore"
Cohesion: 0.11
Nodes (11): PendingActionDao, PendingActionEntity, OfflineSyncStore, PendingActionType, SCROBBLE, STAR, STAR_ALBUM, STAR_ARTIST (+3 more)

### Community 82 - ".childrenOf"
Cohesion: 0.16
Nodes (3): AutoBrowse, Node, AutoBrowseTest

### Community 83 - ".decide"
Cohesion: 0.24
Nodes (5): Beside, NextLineEnd, Placement, RowLayout, RowLayoutTest

### Community 87 - ".agent/skills/glance-canonical-layouts/references/collections/ActionListAppWidget.kt"
Cohesion: 0.21
Nodes (4): ActionListAppWidget, ActionListAppWidgetAppWidgetReceiver, ActionListAppWidget, ActionListAppWidgetAppWidgetReceiver

### Community 90 - ".map"
Cohesion: 0.18
Nodes (3): LoginErrorMapper, LoginFailure, LoginErrorMapperTest

### Community 91 - "StorageScreen"
Cohesion: 0.21
Nodes (19): SettingControlRow(), SettingsCard(), SettingsDivider(), SettingsSection(), SwitchRow(), AboutScreen(), AppearanceScreen(), BitrateChoiceRow() (+11 more)

### Community 92 - "NavidromeLyricsProvider"
Cohesion: 0.12
Nodes (23): FileTags LyricsProvider, Legacy Subsonic getLyrics, OpenSubsonic getLyricsBySongId, LRCLIB Lyrics API, LRCLIB LyricsProvider, LrcParser, LrcParserTest, Lyrics (+15 more)

### Community 93 - "FakeImageGridDataRepository"
Cohesion: 0.17
Nodes (5): FakeImageGridDataRepository, ImageGridItemBackendData, ImageUtils, FakeImageGridDataRepository, ImageGridItemBackendData

### Community 94 - ".agent/skills/glance-canonical-layouts/references/collections/layout/RoundedScrollingLazyColumn.kt"
Cohesion: 0.23
Nodes (4): RoundedScrollingLazyColumn(), RoundedScrollingLazyVerticalGrid(), RoundedScrollingLazyColumn(), RoundedScrollingLazyVerticalGrid()

### Community 95 - "Path"
Cohesion: 0.20
Nodes (17): default_root(), parser(), create_workflow(), default_root(), finish_locked(), finish_workflow(), finished_path(), lock_path() (+9 more)

### Community 96 - "NetworkMonitor"
Cohesion: 0.16
Nodes (8): NetworkClass, MOBILE, OTHER, WIFI, NetworkMonitor, onAvailable(), onCapabilitiesChanged(), onLost()

### Community 97 - "ReplayGainTest"
Cohesion: 0.14
Nodes (6): ReplayGain, ReplayGainMode, ALBUM, OFF, TRACK, ReplayGainTest

### Community 100 - "FakeImageTextListDataRepository"
Cohesion: 0.19
Nodes (4): DemoDataItem, FakeImageTextListDataRepository, DemoDataItem, FakeImageTextListDataRepository

### Community 101 - "FakeTextWithImageRepository"
Cohesion: 0.17
Nodes (6): DemoData, FakeTextWithImageRepository, ImageData, TextData, DemoData, FakeTextWithImageRepository

### Community 102 - "Test"
Cohesion: 0.17
Nodes (3): OfflineRadio, CacheSizePolicyTest, OfflineRadioTest

### Community 103 - "DownloadsScreen"
Cohesion: 0.15
Nodes (13): TrackListControlsRow(), AppTopBar(), TopBarSearchField(), ActiveRow(), DoneRow(), DownloadsScreen(), ProgressThumb(), SectionLabel() (+5 more)

### Community 105 - ".compute"
Cohesion: 0.25
Nodes (4): Result, Segment, UsageBarMath, UsageBarMathTest

### Community 107 - "PlaylistAddSpeedTest"
Cohesion: 0.15
Nodes (3): MetadataCacheKeys, FakeCacheDao, PlaylistAddSpeedTest

### Community 108 - "LibraryScreen"
Cohesion: 0.20
Nodes (14): EmptyState(), CollageCell(), PlaylistCoverArt(), PlaylistCoverArtSquare(), CachedSongsRow(), LibrarySearchField(), SortBottomSheet(), DownloadsTab() (+6 more)

### Community 111 - "CachePolicy"
Cohesion: 0.26
Nodes (3): CachePolicy, CachePolicyTest, FakeClock

### Community 113 - ".state"
Cohesion: 0.25
Nodes (3): QueueHighlight, State, QueueHighlightTest

### Community 116 - ".parse"
Cohesion: 0.25
Nodes (3): LrcParser, Tag, LrcParserTest

### Community 117 - "LocalAudioCoverFetcher.kt"
Cohesion: 0.20
Nodes (4): Factory, LocalAudioCoverFetcher, UpstreamLogger, DataSource

### Community 123 - "build.yml"
Cohesion: 0.11
Nodes (17): name, runs-on, timeout-minutes, concurrency, cancel-in-progress, group, jobs, build (+9 more)

### Community 124 - "PlayerManager"
Cohesion: 0.18
Nodes (18): BoundedQueuePlayer, docs/perf/playlist-add-9.1.md, docs/perf/shuffle-9.1.md, DownloadRepository, DownloadScheduler, DownloadWorker, LyricsMenuSheet, LyricsScrollEngine (+10 more)

### Community 126 - "Vusic (Feishin Mobile)"
Cohesion: 0.15
Nodes (15): AndroidManifest.xml, CHANGELOG.md, Device Files (local playback), docs/perf/downloads-8.4.md, docs/perf/library-8.3.md, EncryptedSharedPreferences (Subsonic credentials), Navidrome Server, P9-C: Dynamic Theme Label Rename (+7 more)

### Community 127 - "QualityPolicy"
Cohesion: 0.20
Nodes (3): QualityPolicy, StreamQuality, QualityPolicyTest

### Community 128 - ".results"
Cohesion: 0.22
Nodes (5): Album, Artist, SearchGrouping, TopHit, SearchGroupingTest

### Community 129 - "CredentialsStore"
Cohesion: 0.18
Nodes (3): Credentials, CredentialsStore, NowPlayingNudge

### Community 130 - "QueueStore.kt"
Cohesion: 0.22
Nodes (3): PersistedTrack, QueueSnapshot, QueueStore

### Community 132 - ".map"
Cohesion: 0.21
Nodes (6): WidgetLayout, COMPACT, LARGE, WidgetLayoutPicker, WidgetStateMapper, WidgetStateTest

### Community 133 - "attributes"
Cohesion: 0.12
Nodes (16): description, label, options, placeholder, render, body, attributes, id (+8 more)

### Community 134 - "Phase 8.3 Status (1.1.3)"
Cohesion: 0.12
Nodes (17): CachePolicy, CacheSizePolicy, DataStore, docs/perf/library-8.3.md, docs/reference/after-8.2/, docs/reference/after-8.3/, docs/reference/before-8.2/, docs/reference/before-8.3/ (+9 more)

### Community 136 - "SettingsViewModel.kt"
Cohesion: 0.12
Nodes (6): Failed, Idle, Ok, StorageStats, Testing, TestState

### Community 138 - "Vusic (Feishin) Project Instructions & Mandatory Skills"
Cohesion: 0.14
Nodes (16): anr-audit-and-fix Skill, Architecture & Coding Standards (UDF, thread safety, media playback), compose-animations Skill, compose-component-design Skill, compose-focus-navigation Skill, compose-performance Skill, compose-state-and-effects Skill, edge-to-edge Skill (+8 more)

### Community 140 - "SortChoice"
Cohesion: 0.23
Nodes (4): LibrarySorts, Option, SortChoice, LibrarySortTest

### Community 145 - "data layer (Retrofit Subsonic API, stores, Room, repositories)"
Cohesion: 0.17
Nodes (15): com.feishin.mobile :app module, AuthInterceptor, AuthRepository (connect/probe/logout), core/ui Contrast (srgb<->Lab, enforce), CoverArtUrlProvider, DarkToneSpec, data layer (Retrofit Subsonic API, stores, Room, repositories), di layer (Hilt modules) (+7 more)

### Community 146 - "PlayerManager singleton"
Cohesion: 0.19
Nodes (15): CacheWriter-based prefetch, Offline mode + Room JSON cache, playback layer (PlayerManager, PlaybackService, scrobble, cache), PlaybackService / PlayerService (MediaSessionService), PlayerManager singleton, QueueOps, QueueStore (DataStore persistence), Radio auto-DJ (+7 more)

### Community 147 - "ui layer (Compose NavHost, screens, viewmodels, components)"
Cohesion: 0.19
Nodes (15): CrashGuard, DataStore Preferences settings, LocalMiniClearance, MiniPlayer, PagerPeek, RowLayout.decide, SettingControlRow, SheetPhysics (+7 more)

### Community 148 - "P9-B: Device Files"
Cohesion: 0.16
Nodes (15): Android Auto service, android-permissions-security skill, Lyrics Settings Section, Lyrics Sync Engine, MediaStore Audio, P9-A: Synced Lyrics, P9-B: Device Files, docs/reference/phase9/ reference screenshots (+7 more)

### Community 149 - "Compose component design skill"
Cohesion: 0.17
Nodes (15): Animation Decision Framework, clip-path for Animation, Compose animations skill, Compose component design skill, Compose focus navigation skill, compose-performance skill, Compose state and effects skill, Compose UI testing patterns skill (+7 more)

### Community 152 - ".partition"
Cohesion: 0.27
Nodes (5): Partition, PlaylistDuplicates, PendingDupCheck, PlaylistDuplicatesTest, track()

### Community 153 - ".d"
Cohesion: 0.18
Nodes (3): VLog, EventListener, Listener

### Community 156 - "Popen"
Cohesion: 0.18
Nodes (6): emit_heartbeat(), terminate_child(), wait_for_child(), emit_heartbeat(), terminate_child(), wait_for_child()

### Community 161 - "SettingsStore.kt"
Cohesion: 0.15
Nodes (4): ThemeMode, DARK, LIGHT, SYSTEM

### Community 163 - "FakeCoverStore"
Cohesion: 0.41
Nodes (3): FakeCoverStore, FakeDownload, OfflineCoverStoreTest

### Community 165 - "with"
Cohesion: 0.15
Nodes (13): steps, if, name, run, uses, with, cache-read-only, distribution (+5 more)

### Community 168 - "Lyrics provider chain"
Cohesion: 0.23
Nodes (12): BetterLyrics, Configurable lyrics sources, KuGou, LRCLIB, lyrics_cache (Room), lyricsProbe test, Lyrics reliability, OpenSubsonic structured lyrics fallback (+4 more)

### Community 169 - "Phase 8.4 Status (1.1.4)"
Cohesion: 0.17
Nodes (12): DeviceFilesFilter, docs/perf/downloads-8.4.md, docs/reference/after-8.4/, docs/reference/before-8.4/, Device Files (Local Music), LoginScreen, LoginViewModel, LRCLIB Client (+4 more)

### Community 170 - "MessageHost"
Cohesion: 0.20
Nodes (12): CachePolicy, CachePolicyTest, DownloadWorker, LibraryRefresh, LoginScreen, LoginViewModel, MessageHost, MetadataCache (+4 more)

### Community 172 - "PlaybackSource"
Cohesion: 0.24
Nodes (5): Kind, LOCAL, STREAM, PlaybackSource, PlaybackSourceTest

### Community 175 - "FakeDownloadQueueManager"
Cohesion: 0.47
Nodes (3): ClearDownloadQueueTest, FakeDownload, FakeDownloadQueueManager

### Community 177 - "Android Permissions and IPC Security Skill"
Cohesion: 0.25
Nodes (11): Android Permissions and IPC Security Skill, CallerVerifier, <permission> element guide, Permissions overview, Photo Picker API guide, ProtectedReceiver, Request runtime permissions guide, RuntimePermissionsActivity (+3 more)

### Community 180 - "LayoutMode"
Cohesion: 0.20
Nodes (4): LayoutMode, GRID, LIST, Settings

### Community 184 - "Project Assumptions and Verification Log"
Cohesion: 0.20
Nodes (10): AppNav, Project Assumptions and Verification Log, AuthRepository, MessageHost, MiniPlayer, NavRules, PlaybackProgress, RadioFetcher (+2 more)

### Community 188 - "Gradle Run Skill"
Cohesion: 0.29
Nodes (8): Bounded Summary as Evidence, Handoff Brief, Subagent Selection and Handoff Reference, Repeated Failure Fingerprint, Gradle Run Skill, gradle_run.py Wrapper Script, Status Uncertainty Handling, Workflow Lifecycle (create/run/status/finish)

### Community 191 - "SettingsRoute"
Cohesion: 0.25
Nodes (8): SettingsRoute, ABOUT, APPEARANCE, CONNECTIVITY, HOME, SERVER, SOUND, STORAGE

### Community 196 - "Lyrics menu"
Cohesion: 0.29
Nodes (7): ActionTile, Dual-line romanization, Full Japanese morphological romanization, Kuromoji IPADIC, Adjustable lyrics line gap, Lyrics menu, Romaji morphological word joining

### Community 197 - "extract_diagnostics"
Cohesion: 0.53
Nodes (6): add_fingerprint(), Diagnostics, extract_diagnostics(), finish_failure_block(), fingerprint(), normalize()

### Community 201 - "TextWithImageLayoutSize"
Cohesion: 0.40
Nodes (5): TextWithImageLayoutSize, HorizontalLarge, HorizontalSmall, VerticalLarge, VerticalSmall

### Community 202 - "interface"
Cohesion: 0.40
Nodes (4): interface, default_prompt, display_name, short_description

### Community 204 - "graphify"
Cohesion: 0.40
Nodes (4): graphify, graphify-out/graph.json, graphify-out/GRAPH_REPORT.md, graphify-out/wiki/index.md

### Community 205 - "TextWithImageLayoutSize"
Cohesion: 0.40
Nodes (5): TextWithImageLayoutSize, HorizontalLarge, HorizontalSmall, VerticalLarge, VerticalSmall

### Community 206 - "interface"
Cohesion: 0.40
Nodes (4): interface, default_prompt, display_name, short_description

### Community 212 - "LyricsRepository"
Cohesion: 0.40
Nodes (5): BetterLyricsProvider, KuGouLyricsProvider, LrclibLyricsProvider, LrclibMatcher, LyricsRepository

### Community 213 - "Error Handling Patterns skill"
Cohesion: 0.40
Nodes (5): CircuitBreaker, ErrorCollector, Error Handling Patterns skill, retry decorator, with_fallback

### Community 214 - "DeviceFilesFilter"
Cohesion: 0.40
Nodes (5): DeviceFilesFilter, DeviceFilesFilterTest, DeviceFilesScreen, DeviceFilesViewModel, LibraryScreen

### Community 219 - "Online lyrics providers (BetterLyrics, LRCLIB, KuGou)"
Cohesion: 0.67
Nodes (3): Kuromoji IPADIC morphological analysis, Online lyrics providers (BetterLyrics, LRCLIB, KuGou), Paxsenix lyrics provider

## Knowledge Gaps
- **260 isolated node(s):** `TracksAdded`, `PassToList`, `None`, `Large`, `Medium` (+255 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 925 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **108 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MusicDataRepository` connect `MusicDataRepository` to `NowPlayingScreen.kt`, `FakeMusicRepo`, `SettingsViewModel.kt`, `HomeViewModel.kt`, `PlaylistEvents`, `DownloadRepository`, `PlayerManager`, `SettingsViewModel`, `MusicRepository`, `RoundedCornerShape`, `MainScaffold`, `Track`, `RadioFakeRepo`, `LibraryViewModel`, `UiState`, `IconButton`, `PlayerService.kt`, `ArtistScreen`, `PlayerManager.kt`, `RepoModule.kt`, `SearchViewModel`, `FavFakeRepo`, `NowPlayingScreen`, `OfflineSyncStore`, `RadioFetcher.kt`, `DownloadsScreen`, `LibraryScreen`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **What connects `TracksAdded`, `PassToList`, `None` to the rest of the system?**
  _260 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `NowPlayingScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.027530364372469637 - nodes in this community are weakly interconnected._
- **Why does `StructuredLyricsDto` connect `NavidromeLyricsProvider` to `Models.kt`?**
  _High betweenness centrality (0.080) - this node is a cross-community bridge._
- **Should `.agents/skills/glance-canonical-layouts/references/text/layout/TextWithImageLayout.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.041211277410372435 - nodes in this community are weakly interconnected._
- **Should `Models.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.055728625822083766 - nodes in this community are weakly interconnected._
- **Should `Contrast` be split into smaller, more focused modules?**
  _Cohesion score 0.05638536221060493 - nodes in this community are weakly interconnected._