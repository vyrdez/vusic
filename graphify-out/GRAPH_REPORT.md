# Graph Report - Feishin  (2026-10-28)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 6011 nodes · 16829 edges · 239 communities (143 shown, 96 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 471 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9b4b50e0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Glance Home Widget
- Glance Layout Dimensions
- Now Playing Lyrics UI
- Color Science Utilities
- Playlist Batching Lyrics Parsing
- Subsonic API DTOs
- Reusable Media Cards
- Glance Toolbar Buttons
- Fake Repository Test Doubles
- App Scaffold Navigation
- Settings UI Controls
- Media Cover Composables
- Settings Preferences Store
- Glance Toolbar Grids
- Player Queue Manager
- Cache Dependency Injection
- Settings ViewModel
- UI State List Helpers
- Lyrics Model Parsing
- Canonical Layout Demo
- Favorites Store Events
- Download Repository
- Expressive Toolbar Layouts
- Playlist Ordering
- Gesture Sheet Physics
- Offline Radio Fetching
- Checklist Layout Variants
- Gradle Run Process Tests
- Glance Text Image Layouts
- Auth Library Refresh
- Login Screen Layout
- Gradle Run Process Tests
- Mini Player UI
- Lyrics Scroll Engine
- Full Bleed Image Widget
- Gradle Run Script Helpers
- ReplayGain Normalization
- Domain Mappers
- Rounded Scrolling Layouts
- Music Data Repository
- Library ViewModel
- Fake Library Repository
- Bounded Queue Player
- Feature Checklist
- Mobile Data Policy
- Stream Quality Policy
- Download State Machine
- LRCLIB Lyrics Provider
- Gradle Process Lifecycle Tests
- Queue Handoff Gestures
- Download DAO Queries
- Release Feature Summary
- Gradle Run Script Helpers
- Network Monitoring
- Shuffle Plan
- Device Files ViewModel
- Lyrics Search ViewModels
- Compose Performance Reference
- Cached Track Store
- Music Database Schema
- Library Filtering Sorting
- Radio Programmer
- Player Service
- Lyrics Scroll Corrections
- Glance Long Text Layout
- Mini Player Gestures
- Color Contrast Utilities
- Gradle Run Status Tests
- Avatar LRU Cache
- Queue Operations Tests
- Lyrics Provider Parsers
- Gradle Run Status Tests
- Script Romanizer
- Navigation Routes Rules
- Project Module Overview
- Glance Image Grid Layout
- Home Screen Sections
- Cache Limit Tests
- Search Toolbar Layouts
- Android Auto Callbacks
- Glance Action Demo
- Search Result Grouping
- Subsonic Authentication
- Download Scheduler
- Lyrics Matching Tests
- Cache Policy Reference
- Auto Browse Tree
- Adaptive Row Layout
- Refresh Rate Selection
- Offline Sync Store
- Lyrics Provider Implementations
- Network DI Module
- Lyrics ViewModel
- Settings DataStore
- Checklist Fake Repository
- Cache Size Policy
- Embedded Lyrics Extractor
- Track Media Mapping
- Lyrics Pipeline Providers
- Main Activity Theme Setup
- Workflow Lock Script
- Detail Screen ViewModels
- Metadata Cache Keys
- Widget Update Handling
- Image Grid Data Repository
- Image Text List Repository
- Text With Image Repository
- Image Bitmap Utilities
- Artist Info Parsing Tests
- Playback Progress Formatting
- Usage Bar Math
- Database Module Providers
- Scrobble Submission Policy
- Lyrics Repository Caching
- Player Listener Bridge
- Settings Library Counts
- Action List Data Repository
- Cache Refresh Policy
- Queue Highlight Logic
- Queue Store Persistence Tests
- Long Text App Widget
- Queue Reorder Commit
- LRC Lyrics Parser
- Credentials Store Auth
- Cache Eviction Limits
- Player Dependency Module
- Text Romanization Settings
- CI Build Workflow
- Performance Optimization Docs
- Compose Animation References
- Long Text Repository
- App Build Configuration
- Subsonic Auth Interceptor
- KuGou Lyrics Provider
- Queue Persistence Store
- Login Error Mapping
- Widget State Layout
- Bug Report Template
- Library Refresh Caching
- Expressive Toolbar Widget
- Font Sizing Utilities
- Download State Machine
- KRC Lyrics Decoder
- Now Playing Widget
- Android Skill Definitions
- Workflow Lifecycle Commands
- Artist Track Ordering
- Pager Peek Math
- Player Connection Lifecycle
- Metadata Cache Storage
- Download Dependency Module
- App Architecture Plan
- Playback Layer Design
- Mini Player UI
- Media Playback Service
- Compose Skill Guides
- Device Files Filtering
- Playlist Duplicate Partition
- Download Notifications
- Dynamic SSL Trust Manager
- Redacting HTTP Logger
- Main Activity Lifecycle
- Process Heartbeat Termination
- Auto Browse Device Tests
- Cache Logging Utilities
- Cover Art URL Builder
- Lyrics Cache Key
- Lyrics Pick Upgrade
- Local Audio Cover Fetcher
- Application Image Loader Setup
- Offline Cover Store
- DTO Parsing Tests
- Queue Shuffle Store Tests
- CI Build Steps
- Theme Mode Migration
- Queue Tap Toggle Behavior
- Lyrics Provider Chain
- Device Files Release Notes
- Release Phase Status
- Pass Through Overscroll
- Playback Source Resolution
- Progress Update Throttle
- Stream URL Provider
- Top Level Title Bar
- Clear Download Queue
- Queue Radio State Tests
- Android Permissions Security
- Shuffle Pipeline Tests
- Stable Cache Key
- Lyrics Selection Logic
- Widget Action Receiver
- App Navigation Rules
- Library Sort Options
- Cache Warmup Entry
- Storage Stats Management
- Gradle Workflow Skill
- Coil Bitmap Loader
- App Settings Screens
- Download Concurrency Tests
- Image Aspect Ratio Utils
- Crash Guard Setup
- Play Queue Operations
- Cached Membership Eviction
- Romanizer Joining Tests
- Lyrics Queue Strip Layout
- Japanese Lyrics Romanization
- Compose Styles Guides
- Storage Limit Thresholds
- Lyrics Tab State Tests
- Gradle Run Skill Config
- Workflow Busy Lock
- Graphify Agent Docs
- Gradle Run Skill Metadata
- Now Playing Bar Tint
- Lyrics Provider Integrations
- Error Handling Patterns
- Device Files Browser
- ANR Trace Analysis
- Brand Identity Guidelines
- Result Type Utilities
- Now Playing Nudge
- Display Change Listener
- KRC Lyrics Parser Tests
- Lyrics Provider Catalog
- Dark Tone Spec
- Android Benchmark Comparison
- Android CLI Skill
- DTO Model Definitions
- Redacting HTTP Logger
- Settings DataStore
- Token Efficiency Skill

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
- **ANR Audit and Trace Diagnosis Workflow** — agent_skills_anr_audit_and_fix_skill, agent_skills_anr_audit_and_fix_references_risk_analysis_guide, agent_skills_anr_audit_and_fix_references_trace_analysis_guide, agent_skills_anr_audit_and_fix_references_anr_trace_report_template [INFERRED 0.85]
- **Brand Identity Source-of-Truth Guidelines** — agent_skills_brand_identity_skill, agent_skills_brand_identity_resources_design_tokens, agent_skills_brand_identity_resources_tech_stack, agent_skills_brand_identity_resources_voice_tone [INFERRED 0.85]
- **Compose Performance Diagnosis Workflow** — agent_skills_compose_performance_skill, agent_skills_compose_performance_references_diagnosis, agent_skills_compose_performance_references_stability, agent_skills_compose_performance_references_deferred_reads, agent_skills_compose_performance_references_composition_contracts [INFERRED 0.85]
- **Compose Phase Deferral Pattern** — agent_skills_jetpack_compose_performance_skill_phase_deferral, agent_skills_jetpack_compose_performance_references_animation_animation, agent_skills_jetpack_compose_performance_references_layouts_layouts, agent_skills_jetpack_compose_performance_references_graphics_performance_draw_with_cache [INFERRED 0.85]
- **Lyrics tab UI experience** — changelog_lyrics_display, changelog_fluid_lyrics_animation, changelog_lyrics_edge_fade, changelog_lyrics_tab_sync, changelog_queue_bottom_strip, changelog_lyrics_menu, changelog_lyrics_line_gap, changelog_now_playing_screen [INFERRED 0.85]

## Communities (239 total, 96 thin omitted)

### Community 0 - "Glance Home Widget"
Cohesion: 0.05
Nodes (22): ActionListAppWidget, ActionListAppWidgetAppWidgetReceiver, CheckListAppWidget, CheckListAppWidgetReceiver, ImageGridAppWidget, ImageGridAppWidgetReceiver, WidgetContent(), ImageTextListAppWidget (+14 more)

### Community 1 - "Glance Layout Dimensions"
Cohesion: 0.06
Nodes (18): CheckListLayoutDimensions, CheckListLayoutTextStyles, ImageGridLayoutDimensions, ImageGridLayoutTextStyles, WidgetTextDimensions, ImageData, TextData, TextWithImageLayoutDimensions (+10 more)

### Community 2 - "Now Playing Lyrics UI"
Cohesion: 0.04
Nodes (27): NowPlayingPalette, formatDuration(), MarqueeTitle(), EditLyricsDialog(), formatLyricsForCopy(), formatLyricsForEdit(), formatToLrc(), LyricsMenuSheet() (+19 more)

### Community 3 - "Color Science Utilities"
Cohesion: 0.06
Nodes (14): Contrast, Hsl, NowPlayingColors, PlayingColors, PlayingScheme, animatedPlayingScheme(), PlayingSchemeCache, rememberPlayingScheme() (+6 more)

### Community 4 - "Playlist Batching Lyrics Parsing"
Cohesion: 0.06
Nodes (7): PlaylistBatch, SyncedLyrics, ListSerializer(), MusicRepository, RepoModule, PlaylistBatchTest, SyncedLyricsTest

### Community 5 - "Subsonic API DTOs"
Cohesion: 0.08
Nodes (44): AlbumDetailDto, AlbumDto, AlbumDtoListSerializer, AlbumList2Dto, AlbumListDto, ArtistDetailDto, ArtistDto, ArtistDtoListSerializer (+36 more)

### Community 6 - "Reusable Media Cards"
Cohesion: 0.07
Nodes (45): RoundedCornerShape, FULL, MEDIUM, IconButton(), ArtistAvatar(), AlbumCard(), ArtistCard(), ClickableName() (+37 more)

### Community 7 - "Glance Toolbar Buttons"
Cohesion: 0.06
Nodes (38): PillShapedButton(), RectangularIconButton(), RoundedCornerShape, FULL, MEDIUM, SearchBar(), SearchIconButton(), SearchToolBarButton (+30 more)

### Community 8 - "Fake Repository Test Doubles"
Cohesion: 0.04
Nodes (6): album(), FakeCacheBytes, FakeCachedTrackDao, FakeMusicRepo, track(), ViewModelStateTest

### Community 9 - "App Scaffold Navigation"
Cohesion: 0.04
Nodes (19): MessageHost(), AppNavigationBar(), AppTopBar(), goToLibraryTab(), goToTab(), MainScaffold(), TopBarSearchField(), AppNav() (+11 more)

### Community 10 - "Settings UI Controls"
Cohesion: 0.06
Nodes (31): SegmentedControl(), SettingControlRow(), SettingRowText(), SettingsActionButton(), SettingsControlBlock(), SettingsNote(), UsageLegendRow(), RowText() (+23 more)

### Community 11 - "Media Cover Composables"
Cohesion: 0.05
Nodes (15): Favorites, Placeholder, PlaylistCover, DownloadIndicator(), OfflineBanner(), CollageCell(), PlaylistCoverArt(), PlaylistCoverArtSquare() (+7 more)

### Community 13 - "Glance Toolbar Grids"
Cohesion: 0.08
Nodes (34): HeaderTwoRowGrid(), SideBarTwoRowGrid(), SpacedColumn(), SpacedRow(), TwoRowGrid(), FluidContentIconButton(), FluidHeaderAppIcon(), FluidHeaderIconButton() (+26 more)

### Community 14 - "Player Queue Manager"
Cohesion: 0.09
Nodes (3): ClearedQueue, PlayerManager, QueueItem

### Community 15 - "Cache Dependency Injection"
Cohesion: 0.07
Nodes (14): CacheModule, AlbumSnap, DiskHomeCache, HomeCache, HomeSnapshot, InMemoryHomeCache, TrackSnap, AlbumTarget (+6 more)

### Community 17 - "UI State List Helpers"
Cohesion: 0.07
Nodes (24): Data, Empty, Error, Loading, UiState, rememberSavableListState(), UiMessenger, ArtistCollapsedTopBar() (+16 more)

### Community 18 - "Lyrics Model Parsing"
Cohesion: 0.08
Nodes (23): DebugLyricsFixtures, fromSynced(), LyricLine, Lyrics, LyricsKind, INSTRUMENTAL, NONE, PLAIN (+15 more)

### Community 19 - "Canonical Layout Demo"
Cohesion: 0.07
Nodes (12): CanonicalLayoutActivity, CanonicalLayoutRow(), CanonicalLayoutRowData, Content(), Header(), requestPin(), CanonicalLayoutActivity, CanonicalLayoutRow() (+4 more)

### Community 20 - "Favorites Store Events"
Cohesion: 0.09
Nodes (29): FavoritesChanged, FavoritesStore, PlaylistEvent, PlaylistEvents, TracksAdded, speedDialKey(), ActionTile(), ActionTiles() (+21 more)

### Community 21 - "Download Repository"
Cohesion: 0.06
Nodes (5): removeIf(), DownloadGroupEntity, DownloadProgress, DownloadRepository, observeAllSnapshot()

### Community 22 - "Expressive Toolbar Layouts"
Cohesion: 0.12
Nodes (29): AllButtonsScaledLayout(), CenterButton(), CenterButtonOnlyLayout(), checkCornerButtonsListSize(), CornerButton(), CornerButtonsGrid(), ExpressiveToolBarButton, ExpressiveToolbarLayout() (+21 more)

### Community 23 - "Playlist Ordering"
Cohesion: 0.09
Nodes (9): PlaylistOrdering, PlaylistSort, PlaylistSortKey, ARTIST, DATE_ADDED, DURATION, PLAYLIST_ORDER, TITLE (+1 more)

### Community 24 - "Gesture Sheet Physics"
Cohesion: 0.09
Nodes (3): SheetPhysics, SheetPhysicsTest, LyricsAnimationTest

### Community 25 - "Offline Radio Fetching"
Cohesion: 0.07
Nodes (6): OfflineRadio, RadioFetcher, toDomainTrack(), RadioFakeRepo, RadioFetcherTest, track()

### Community 26 - "Checklist Layout Variants"
Cohesion: 0.08
Nodes (34): ActionListLayout(), Content(), GridView(), ListView(), CheckListItem(), CheckListLayout(), CheckListLayoutSize, Large (+26 more)

### Community 27 - "Gradle Run Process Tests"
Cohesion: 0.09
Nodes (4): GradleRunProcessTest, GradleRunTestCase, GradleRunWorkflowTest, process_is_running()

### Community 28 - "Glance Text Image Layouts"
Cohesion: 0.09
Nodes (28): HorizontalContent(), Image(), maybeClickable(), NoDataContent(), TextStack(), TextWithImageData, TextWithImageLayout(), TextWithImageLayoutSize (+20 more)

### Community 29 - "Auth Library Refresh"
Cohesion: 0.07
Nodes (4): AuthRepository, LibraryRefresh, LoginUi, LoginViewModel

### Community 30 - "Login Screen Layout"
Cohesion: 0.09
Nodes (6): ListItem(), VerticalListItem(), ListItem(), VerticalListItem(), LoginMessageCard(), LoginScreen()

### Community 31 - "Gradle Run Process Tests"
Cohesion: 0.10
Nodes (3): GradleRunProcessTest, GradleRunTestCase, GradleRunWorkflowTest

### Community 33 - "Mini Player UI"
Cohesion: 0.07
Nodes (3): MiniPlayer(), MiniPlayerRing(), Motion

### Community 34 - "Lyrics Scroll Engine"
Cohesion: 0.10
Nodes (8): computeScrollDelta(), computeScrollDurationMs(), computeScrollOffset(), evaluateRetargetDelta(), LyricsDisplayTokens, shouldAutoScroll(), LyricsLineGapTest, LyricsScrollEngineTest

### Community 35 - "Full Bleed Image Widget"
Cohesion: 0.14
Nodes (14): ImageGridItemData, NoDataContent(), FullBleedImageAppWidget, FullBleedImageAppWidgetReceiver, FullBleedImageLayout(), FullBleedImageLayoutPreview(), GalleryItemCard(), SnapScrollingGallery() (+6 more)

### Community 36 - "Gradle Run Script Helpers"
Cohesion: 0.10
Nodes (26): add_fingerprint(), append_run(), bounded_summary(), Diagnostics, display_command(), effective_command(), emit_summary(), extract_diagnostics() (+18 more)

### Community 37 - "ReplayGain Normalization"
Cohesion: 0.09
Nodes (8): ReplayGain, ReplayGainMode, ALBUM, OFF, TRACK, CacheSizePolicyTest, OfflineRadioTest, ReplayGainTest

### Community 38 - "Domain Mappers"
Cohesion: 0.18
Nodes (17): toDomain(), Album, AlbumDetail, Artist, ArtistDetail, ArtistInfo, Genre, Playlist (+9 more)

### Community 39 - "Rounded Scrolling Layouts"
Cohesion: 0.11
Nodes (4): RoundedScrollingLazyColumn(), RoundedScrollingLazyVerticalGrid(), RoundedScrollingLazyColumn(), RoundedScrollingLazyVerticalGrid()

### Community 41 - "Library ViewModel"
Cohesion: 0.09
Nodes (8): LibraryTab, ALBUMS, ARTISTS, DOWNLOADS, FAVORITES, PLAYLISTS, SONGS, LibraryViewModel

### Community 42 - "Fake Library Repository"
Cohesion: 0.07
Nodes (3): FavFakeRepo, FavoritesStoreTest, track()

### Community 43 - "Bounded Queue Player"
Cohesion: 0.09
Nodes (4): BoundedQueuePlayer, Timeline, BoundedQueuePlayerTest, FakeTimeline

### Community 44 - "Feature Checklist"
Cohesion: 0.09
Nodes (38): Accessibility, Add to playlist, Android Auto, Clear all download queue, Volume-ramp crossfade, Device Files in Library, Download auto-resume after reconnect, Dynamic Theme (+30 more)

### Community 45 - "Mobile Data Policy"
Cohesion: 0.10
Nodes (4): MobileDataPolicy, DownloadsUi, DownloadsViewModel, MobileDataPolicyTest

### Community 46 - "Stream Quality Policy"
Cohesion: 0.10
Nodes (4): QualityPolicy, StreamQuality, PrefetchManager, QualityPolicyTest

### Community 47 - "Download State Machine"
Cohesion: 0.08
Nodes (13): DownloadStateMachine, DownloadStatus, COMPLETED, DOWNLOADING, FAILED, PAUSED, QUEUED, DownloadWorker (+5 more)

### Community 48 - "LRCLIB Lyrics Provider"
Cohesion: 0.12
Nodes (4): LrclibResponseDto, LrclibLyricsProvider, LrclibMatcher, LrclibMatcherTest

### Community 49 - "Gradle Process Lifecycle Tests"
Cohesion: 0.08
Nodes (6): GradleRunHeartbeatTest, GradleRunWindowsProcessTest, process_is_running(), ProcessAssertionTest, GradleRunHeartbeatTest, GradleRunWindowsProcessTest

### Community 50 - "Queue Handoff Gestures"
Cohesion: 0.14
Nodes (6): ListHandoff, PassToList, QueueHandoff, TakeBySheet, NestedScrollConnection, QueueHandoffTest

### Community 51 - "Download DAO Queries"
Cohesion: 0.12
Nodes (3): DownloadDao, DownloadEntity, DownloadGroupDao

### Community 52 - "Release Feature Summary"
Cohesion: 0.09
Nodes (35): ABI splits (arm64-v8a + x86_64) and resConfigs("en"), PlayerManager.adoptExternalQueue, Android Auto browse tree (4 root tabs), App logo (eighth note) and ic_stat_vusic, Hand-written baseline-prof.txt + :baselineprofile module, CHANGELOG 1.0.0, collectAsStateWithLifecycle migration (94 sites / 17 files), DEFAULT_SESSION_AND_LIBRARY_COMMANDS fix (+27 more)

### Community 53 - "Gradle Run Script Helpers"
Cohesion: 0.15
Nodes (28): add_fingerprint(), append_run(), bounded_summary(), Diagnostics, display_command(), effective_command(), emit_summary(), extract_diagnostics() (+20 more)

### Community 54 - "Network Monitoring"
Cohesion: 0.09
Nodes (9): NetworkClass, MOBILE, OTHER, WIFI, StreamUrlProvider, NetworkMonitor, onAvailable(), onCapabilitiesChanged() (+1 more)

### Community 55 - "Shuffle Plan"
Cohesion: 0.13
Nodes (3): ShufflePlan, ShufflePlanTest, BigPlaylistShuffleTest

### Community 56 - "Device Files ViewModel"
Cohesion: 0.10
Nodes (3): DeviceFilesState, DeviceFilesViewModel, ContentObserver

### Community 57 - "Lyrics Search ViewModels"
Cohesion: 0.08
Nodes (7): SearchFilter, ALBUMS, ALL, ARTISTS, PLAYLISTS, SONGS, SearchViewModel

### Community 58 - "Compose Performance Reference"
Cohesion: 0.09
Nodes (33): Animation and Shared Transitions Reference, Persistent Animatable in Modifier.Node, Defer sharedBounds/sharedElement Until Visible, collectAsStateWithLifecycle, Effect Execution Ordering and Teardown Costs, Effects, Lifecycle, and Threading Reference, Side Effects API Selection Guide, AsyncImage over painterResource (+25 more)

### Community 59 - "Cached Track Store"
Cohesion: 0.09
Nodes (5): CacheBytes, CachedTrackStore, Media3CacheBytes, CachedTrackDao, CachedTrackEntity

### Community 60 - "Music Database Schema"
Cohesion: 0.09
Nodes (6): LyricsCacheMigrationTest, CachedCoverDao, CachedCoverEntity, CachedMetadataEntity, LyricsCacheDao, LyricsCacheEntity

### Community 61 - "Library Filtering Sorting"
Cohesion: 0.12
Nodes (4): LibraryOrdering, SortChoice, album(), LibraryOrderingTest

### Community 62 - "Radio Programmer"
Cohesion: 0.13
Nodes (5): RadioMemory, RadioProgrammer, RadioTrigger, RadioProgrammerTest, track()

### Community 64 - "Lyrics Scroll Corrections"
Cohesion: 0.13
Nodes (12): computeEdgeFadeAlpha(), computeLyricsAnchorPx(), computeLyricsBottomPadding(), computeLyricsScrollDelta(), computeLyricsScrollOffset(), resolveReturnScrollAction(), ScrollAction, ANIMATE (+4 more)

### Community 65 - "Glance Long Text Layout"
Cohesion: 0.11
Nodes (21): LongTextLayout(), LongTextLayoutData, LongTextLayoutPreview(), LongTextLayoutSize, Normal, XSmall, maybeClickable(), showTitle() (+13 more)

### Community 67 - "Color Contrast Utilities"
Cohesion: 0.14
Nodes (5): Collage, Server, VusicColorMath, LyricsMenuContrastTest, VusicDesignTest

### Community 69 - "Avatar LRU Cache"
Cohesion: 0.13
Nodes (4): AvatarFallback, Entry, LruSizer, M6PartATest

### Community 71 - "Lyrics Provider Parsers"
Cohesion: 0.10
Nodes (5): TtmlLyricsParser, BetterLyricsProvider, BetterLyricsResponse, LoginFailure, TtmlLyricsParserTest

### Community 74 - "Navigation Routes Rules"
Cohesion: 0.13
Nodes (3): Routes, NavRules, NavRulesTest

### Community 75 - "Project Module Overview"
Cohesion: 0.10
Nodes (29): Android Auto service, app Gradle module, baselineprofile Gradle module, CI pipeline, DataStore settings, EncryptedSharedPreferences credential store, ExoPlayer, Feishin (+21 more)

### Community 76 - "Glance Image Grid Layout"
Cohesion: 0.12
Nodes (18): Grid(), GridItem(), ImageGridLayout(), ImageGridLayoutSize, Large, Medium, Small, ImageOnlyGridLayoutPreview() (+10 more)

### Community 77 - "Home Screen Sections"
Cohesion: 0.15
Nodes (12): SongPagerSection(), AlbumCarousel(), DiscoverCard(), ExplorePill(), HomeContent(), HomeScreen(), HomeTopBar(), MixCard() (+4 more)

### Community 79 - "Search Toolbar Layouts"
Cohesion: 0.16
Nodes (11): SearchToolBarLayout(), SearchToolBarLayoutSize, HeaderTwoRowGrid, HorizontalRow, SideBarTwoRowGrid, TwoByTwoGrid, VerticalColumn, SearchToolBarAppWidget (+3 more)

### Community 81 - "Glance Action Demo"
Cohesion: 0.12
Nodes (7): ActionDemonstrationActivity, EmptyListContent(), ActionUtils, ActionDemonstrationActivity, EmptyListContent(), NoDataContent(), ActionUtils

### Community 82 - "Search Result Grouping"
Cohesion: 0.15
Nodes (7): Album, Artist, SearchGrouping, TopHit, PrefetchWindow, PrefetchWindowTest, SearchGroupingTest

### Community 87 - "Auto Browse Tree"
Cohesion: 0.16
Nodes (3): AutoBrowse, Node, AutoBrowseTest

### Community 88 - "Adaptive Row Layout"
Cohesion: 0.24
Nodes (5): Beside, NextLineEnd, Placement, RowLayout, RowLayoutTest

### Community 89 - "Refresh Rate Selection"
Cohesion: 0.18
Nodes (7): DisplayModeInfo, RefreshRate, RefreshRateMode, AUTO, MAX, STANDARD, RefreshRateTest

### Community 90 - "Offline Sync Store"
Cohesion: 0.11
Nodes (11): PendingActionDao, PendingActionEntity, OfflineSyncStore, PendingActionType, SCROBBLE, STAR, STAR_ALBUM, STAR_ARTIST (+3 more)

### Community 91 - "Lyrics Provider Implementations"
Cohesion: 0.13
Nodes (6): FileTagLyricsProvider, PaxsenixLyricsProvider, LyricsProvider, Hit, LyricsProbeTest, Row

### Community 93 - "Lyrics ViewModel"
Cohesion: 0.14
Nodes (8): LyricsCandidate, Instrumental, Loading, LyricsUi, LyricsViewModel, None, Plain, Synced

### Community 94 - "Settings DataStore"
Cohesion: 0.09
Nodes (8): LayoutMode, GRID, LIST, Settings, ThemeMode, DARK, LIGHT, SYSTEM

### Community 97 - "Embedded Lyrics Extractor"
Cohesion: 0.16
Nodes (3): EmbeddedLyricsExtractor, EmbeddedLyricsExtractorTest, FilterInputStream

### Community 98 - "Track Media Mapping"
Cohesion: 0.20
Nodes (3): TrackMediaInfo, TrackMediaItems, TrackMediaItemsTest

### Community 99 - "Lyrics Pipeline Providers"
Cohesion: 0.12
Nodes (23): FileTags LyricsProvider, Legacy Subsonic getLyrics, OpenSubsonic getLyricsBySongId, LRCLIB Lyrics API, LRCLIB LyricsProvider, LrcParser, LrcParserTest, Lyrics (+15 more)

### Community 101 - "Workflow Lock Script"
Cohesion: 0.21
Nodes (18): default_root(), parser(), create_workflow(), default_root(), finish_locked(), finish_workflow(), finished_path(), lock_path() (+10 more)

### Community 102 - "Detail Screen ViewModels"
Cohesion: 0.14
Nodes (4): toTrack(), AlbumDetailViewModel, ArtistDetailViewModel, PlaylistDetailViewModel

### Community 103 - "Metadata Cache Keys"
Cohesion: 0.15
Nodes (3): MetadataCacheKeys, FakeCacheDao, PlaylistAddSpeedTest

### Community 105 - "Image Grid Data Repository"
Cohesion: 0.19
Nodes (4): FakeImageGridDataRepository, ImageGridItemBackendData, FakeImageGridDataRepository, ImageGridItemBackendData

### Community 106 - "Image Text List Repository"
Cohesion: 0.19
Nodes (4): DemoDataItem, FakeImageTextListDataRepository, DemoDataItem, FakeImageTextListDataRepository

### Community 107 - "Text With Image Repository"
Cohesion: 0.18
Nodes (4): DemoData, FakeTextWithImageRepository, DemoData, FakeTextWithImageRepository

### Community 109 - "Artist Info Parsing Tests"
Cohesion: 0.17
Nodes (3): flowNow(), M45Test, track()

### Community 110 - "Playback Progress Formatting"
Cohesion: 0.20
Nodes (4): PlaybackProgress, dim75(), SeekBar(), PlaybackProgressTest

### Community 111 - "Usage Bar Math"
Cohesion: 0.25
Nodes (4): Result, Segment, UsageBarMath, UsageBarMathTest

### Community 115 - "Player Listener Bridge"
Cohesion: 0.14
Nodes (3): Listener, WidgetState, WidgetStateStore

### Community 116 - "Settings Library Counts"
Cohesion: 0.10
Nodes (6): Failed, Idle, LibraryCounts, Ok, Testing, TestState

### Community 118 - "Cache Refresh Policy"
Cohesion: 0.26
Nodes (3): CachePolicy, CachePolicyTest, FakeClock

### Community 119 - "Queue Highlight Logic"
Cohesion: 0.25
Nodes (3): QueueHighlight, State, QueueHighlightTest

### Community 121 - "Long Text App Widget"
Cohesion: 0.27
Nodes (4): LongTextAppWidget, LongTextAppWidgetReceiver, LongTextAppWidget, LongTextAppWidgetReceiver

### Community 123 - "LRC Lyrics Parser"
Cohesion: 0.25
Nodes (3): LrcParser, Tag, LrcParserTest

### Community 124 - "Credentials Store Auth"
Cohesion: 0.19
Nodes (3): Credentials, CredentialsStore, HostRewriteInterceptor

### Community 128 - "CI Build Workflow"
Cohesion: 0.11
Nodes (17): name, runs-on, timeout-minutes, concurrency, cancel-in-progress, group, jobs, build (+9 more)

### Community 129 - "Performance Optimization Docs"
Cohesion: 0.18
Nodes (18): BoundedQueuePlayer, docs/perf/playlist-add-9.1.md, docs/perf/shuffle-9.1.md, DownloadRepository, DownloadScheduler, DownloadWorker, LyricsMenuSheet, LyricsScrollEngine (+10 more)

### Community 130 - "Compose Animation References"
Cohesion: 0.26
Nodes (17): AnimatedContent Identity Reference, Compose Animations Skill, Compose Modifier and Layout Reference, Compose Slot API Pattern Reference, Compose Component Design Skill, Compose Focus Navigation Skill, Composition Contracts Reference, Compose Deferred Reads Reference (+9 more)

### Community 132 - "App Build Configuration"
Cohesion: 0.15
Nodes (15): AndroidManifest.xml, CHANGELOG.md, Device Files (local playback), docs/perf/downloads-8.4.md, docs/perf/library-8.3.md, EncryptedSharedPreferences (Subsonic credentials), Navidrome Server, P9-C: Dynamic Theme Label Rename (+7 more)

### Community 134 - "KuGou Lyrics Provider"
Cohesion: 0.24
Nodes (7): KugouCandidate, KugouCandidatesResponse, KugouDownloadResponse, KuGouLyricsProvider, KugouSearchData, KugouSearchResponse, KugouSearchSong

### Community 135 - "Queue Persistence Store"
Cohesion: 0.22
Nodes (3): PersistedTrack, QueueSnapshot, QueueStore

### Community 137 - "Widget State Layout"
Cohesion: 0.21
Nodes (6): WidgetLayout, COMPACT, LARGE, WidgetLayoutPicker, WidgetStateMapper, WidgetStateTest

### Community 138 - "Bug Report Template"
Cohesion: 0.12
Nodes (16): description, label, options, placeholder, render, body, attributes, id (+8 more)

### Community 139 - "Library Refresh Caching"
Cohesion: 0.12
Nodes (17): CachePolicy, CacheSizePolicy, DataStore, docs/perf/library-8.3.md, docs/reference/after-8.2/, docs/reference/after-8.3/, docs/reference/before-8.2/, docs/reference/before-8.3/ (+9 more)

### Community 140 - "Expressive Toolbar Widget"
Cohesion: 0.32
Nodes (4): ExpressiveToolbarAppWidget, ExpressiveToolbarAppWidgetReceiver, ExpressiveToolbarAppWidget, ExpressiveToolbarAppWidgetReceiver

### Community 145 - "Android Skill Definitions"
Cohesion: 0.14
Nodes (16): anr-audit-and-fix Skill, Architecture & Coding Standards (UDF, thread safety, media playback), compose-animations Skill, compose-component-design Skill, compose-focus-navigation Skill, compose-performance Skill, compose-state-and-effects Skill, edge-to-edge Skill (+8 more)

### Community 146 - "Workflow Lifecycle Commands"
Cohesion: 0.26
Nodes (13): create_workflow(), finish_locked(), finish_workflow(), finished_path(), lock_path(), main(), managed_root(), read_finished() (+5 more)

### Community 152 - "App Architecture Plan"
Cohesion: 0.17
Nodes (15): com.feishin.mobile :app module, AuthInterceptor, AuthRepository (connect/probe/logout), core/ui Contrast (srgb<->Lab, enforce), CoverArtUrlProvider, DarkToneSpec, data layer (Retrofit Subsonic API, stores, Room, repositories), di layer (Hilt modules) (+7 more)

### Community 153 - "Playback Layer Design"
Cohesion: 0.19
Nodes (15): CacheWriter-based prefetch, Offline mode + Room JSON cache, playback layer (PlayerManager, PlaybackService, scrobble, cache), PlaybackService / PlayerService (MediaSessionService), PlayerManager singleton, QueueOps, QueueStore (DataStore persistence), Radio auto-DJ (+7 more)

### Community 154 - "Mini Player UI"
Cohesion: 0.19
Nodes (15): CrashGuard, DataStore Preferences settings, LocalMiniClearance, MiniPlayer, PagerPeek, RowLayout.decide, SettingControlRow, SheetPhysics (+7 more)

### Community 155 - "Media Playback Service"
Cohesion: 0.16
Nodes (15): Android Auto service, android-permissions-security skill, Lyrics Settings Section, Lyrics Sync Engine, MediaStore Audio, P9-A: Synced Lyrics, P9-B: Device Files, docs/reference/phase9/ reference screenshots (+7 more)

### Community 156 - "Compose Skill Guides"
Cohesion: 0.17
Nodes (15): Animation Decision Framework, clip-path for Animation, Compose animations skill, Compose component design skill, Compose focus navigation skill, compose-performance skill, Compose state and effects skill, Compose UI testing patterns skill (+7 more)

### Community 158 - "Playlist Duplicate Partition"
Cohesion: 0.27
Nodes (5): Partition, PlaylistDuplicates, PendingDupCheck, PlaylistDuplicatesTest, track()

### Community 163 - "Process Heartbeat Termination"
Cohesion: 0.18
Nodes (6): emit_heartbeat(), terminate_child(), wait_for_child(), emit_heartbeat(), terminate_child(), wait_for_child()

### Community 165 - "Cache Logging Utilities"
Cohesion: 0.22
Nodes (4): VLog, EventListener, UpstreamLogger, DataSource

### Community 171 - "Offline Cover Store"
Cohesion: 0.41
Nodes (3): FakeCoverStore, FakeDownload, OfflineCoverStoreTest

### Community 174 - "CI Build Steps"
Cohesion: 0.15
Nodes (13): steps, if, name, run, uses, with, cache-read-only, distribution (+5 more)

### Community 175 - "Theme Mode Migration"
Cohesion: 0.33
Nodes (3): migrateStoredTheme(), resolveDark(), ThemeModeTest

### Community 177 - "Lyrics Provider Chain"
Cohesion: 0.23
Nodes (12): BetterLyrics, Configurable lyrics sources, KuGou, LRCLIB, lyrics_cache (Room), lyricsProbe test, Lyrics reliability, OpenSubsonic structured lyrics fallback (+4 more)

### Community 178 - "Device Files Release Notes"
Cohesion: 0.17
Nodes (12): DeviceFilesFilter, docs/perf/downloads-8.4.md, docs/reference/after-8.4/, docs/reference/before-8.4/, Device Files (Local Music), LoginScreen, LoginViewModel, LRCLIB Client (+4 more)

### Community 179 - "Release Phase Status"
Cohesion: 0.20
Nodes (12): CachePolicy, CachePolicyTest, DownloadWorker, LibraryRefresh, LoginScreen, LoginViewModel, MessageHost, MetadataCache (+4 more)

### Community 181 - "Playback Source Resolution"
Cohesion: 0.24
Nodes (5): Kind, LOCAL, STREAM, PlaybackSource, PlaybackSourceTest

### Community 185 - "Clear Download Queue"
Cohesion: 0.47
Nodes (3): ClearDownloadQueueTest, FakeDownload, FakeDownloadQueueManager

### Community 187 - "Android Permissions Security"
Cohesion: 0.25
Nodes (11): Android Permissions and IPC Security Skill, CallerVerifier, <permission> element guide, Permissions overview, Photo Picker API guide, ProtectedReceiver, Request runtime permissions guide, RuntimePermissionsActivity (+3 more)

### Community 190 - "Stable Cache Key"
Cohesion: 0.27
Nodes (3): CachedMembership, StableCacheKey, StableCacheKeyTest

### Community 193 - "App Navigation Rules"
Cohesion: 0.20
Nodes (10): AppNav, Project Assumptions and Verification Log, AuthRepository, MessageHost, MiniPlayer, NavRules, PlaybackProgress, RadioFetcher (+2 more)

### Community 194 - "Library Sort Options"
Cohesion: 0.31
Nodes (3): LibrarySorts, Option, LibrarySortTest

### Community 197 - "Gradle Workflow Skill"
Cohesion: 0.29
Nodes (8): Bounded Summary as Evidence, Handoff Brief, Subagent Selection and Handoff Reference, Repeated Failure Fingerprint, Gradle Run Skill, gradle_run.py Wrapper Script, Status Uncertainty Handling, Workflow Lifecycle (create/run/status/finish)

### Community 200 - "App Settings Screens"
Cohesion: 0.25
Nodes (8): SettingsRoute, ABOUT, APPEARANCE, CONNECTIVITY, HOME, SERVER, SOUND, STORAGE

### Community 208 - "Japanese Lyrics Romanization"
Cohesion: 0.29
Nodes (7): ActionTile, Dual-line romanization, Full Japanese morphological romanization, Kuromoji IPADIC, Adjustable lyrics line gap, Lyrics menu, Romaji morphological word joining

### Community 209 - "Compose Styles Guides"
Cohesion: 0.33
Nodes (6): Custom Themes in Compose Guide, Compose Styles Fundamentals Guide, Compose Styles Animations and State Styling Guide, Compose Styles versus Modifiers Comparison, Compose Styles Theming Guide, Compose Styles Skill

### Community 212 - "Gradle Run Skill Config"
Cohesion: 0.40
Nodes (4): interface, default_prompt, display_name, short_description

### Community 214 - "Graphify Agent Docs"
Cohesion: 0.40
Nodes (4): graphify, graphify-out/graph.json, graphify-out/GRAPH_REPORT.md, graphify-out/wiki/index.md

### Community 215 - "Gradle Run Skill Metadata"
Cohesion: 0.40
Nodes (4): interface, default_prompt, display_name, short_description

### Community 217 - "Lyrics Provider Integrations"
Cohesion: 0.40
Nodes (5): BetterLyricsProvider, KuGouLyricsProvider, LrclibLyricsProvider, LrclibMatcher, LyricsRepository

### Community 218 - "Error Handling Patterns"
Cohesion: 0.40
Nodes (5): CircuitBreaker, ErrorCollector, Error Handling Patterns skill, retry decorator, with_fallback

### Community 219 - "Device Files Browser"
Cohesion: 0.40
Nodes (5): DeviceFilesFilter, DeviceFilesFilterTest, DeviceFilesScreen, DeviceFilesViewModel, LibraryScreen

### Community 220 - "ANR Trace Analysis"
Cohesion: 0.67
Nodes (4): ANR Trace Report Template, ANR Risk Analysis Guide, ANR Trace Analysis Guide, ANR Audit and Fix Skill

### Community 221 - "Brand Identity Guidelines"
Cohesion: 0.67
Nodes (4): Design Tokens, Brand Tech Stack Guidelines, Voice and Tone Guidelines, Brand Identity Skill

### Community 222 - "Result Type Utilities"
Cohesion: 0.50
Nodes (3): Err, Ok, Result

### Community 228 - "Lyrics Provider Catalog"
Cohesion: 0.67
Nodes (3): Kuromoji IPADIC morphological analysis, Online lyrics providers (BetterLyrics, LRCLIB, KuGou), Paxsenix lyrics provider

## Knowledge Gaps
- **270 isolated node(s):** `TracksAdded`, `PassToList`, `None`, `HeaderTwoRowGrid`, `HorizontalRow` (+265 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 935 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **96 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MusicDataRepository` connect `Music Data Repository` to `Now Playing Lyrics UI`, `Playlist Batching Lyrics Parsing`, `Reusable Media Cards`, `Fake Repository Test Doubles`, `App Scaffold Navigation`, `Media Cover Composables`, `Player Queue Manager`, `Cache Dependency Injection`, `Settings ViewModel`, `UI State List Helpers`, `Favorites Store Events`, `Download Repository`, `Offline Radio Fetching`, `Auth Library Refresh`, `Domain Mappers`, `Library ViewModel`, `Fake Library Repository`, `Network Monitoring`, `Lyrics Search ViewModels`, `Player Service`, `Home Screen Sections`, `Offline Sync Store`, `Detail Screen ViewModels`, `Settings Library Counts`?**
  _High betweenness centrality (0.091) - this node is a cross-community bridge._
- **What connects `TracksAdded`, `PassToList`, `None` to the rest of the system?**
  _270 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Glance Home Widget` be split into smaller, more focused modules?**
  _Cohesion score 0.04828372026776812 - nodes in this community are weakly interconnected._
- **Why does `StructuredLyricsDto` connect `Lyrics Pipeline Providers` to `Subsonic API DTOs`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **Should `Glance Layout Dimensions` be split into smaller, more focused modules?**
  _Cohesion score 0.06427573358174196 - nodes in this community are weakly interconnected._
- **Should `Now Playing Lyrics UI` be split into smaller, more focused modules?**
  _Cohesion score 0.03644688644688645 - nodes in this community are weakly interconnected._
- **Should `Color Science Utilities` be split into smaller, more focused modules?**
  _Cohesion score 0.05765870704717531 - nodes in this community are weakly interconnected._