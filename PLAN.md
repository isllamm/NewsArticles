# Implementation plan

Plan, structure and status for the News Articles task. The README has the short version for reviewers.

## 1. The task

- Two screens: a list of articles with a search field, and a details screen.
- The list comes from two endpoints (`page=1` and `page=2`) merged into one list. Search uses `q={keyword}`.
- Every request sends the header `x-api-key`.
- Handle network failures properly.
- Bonus: offline mode with Room.
- Required: Kotlin, MVI, Retrofit, Coroutines and Flow, Jetpack (ViewModel, Navigation), Hilt, Room.
- Marked on: correctness, structure, state handling, error handling and edge cases, bonus.

## 2. What the API really returns

Checked on 9 October 2026 with the task header.

| Finding | What to do |
|---|---|
| Page sizes vary. Page 1 gave 17 articles and page 2 gave 15 (37 in total). | Never assume 20 per page. |
| `author`, `description`, `urlToImage` and `content` can be null. 2 of the 17 articles on page 1 had no image. | Make every DTO field nullable and show a fallback. |
| No duplicates today, but an article can move from one page to the other between two calls. | Remove duplicates by `url`. |
| A search with no match returns HTTP 200 and an empty list. | Show an empty state, not an error. |
| Errors come as JSON (`status`, `code`, `message`). A bad or missing key gives HTTP 401. NewsAPI also documents `rateLimited` (429). | Map the status and the code to one `AppError`. |
| `content` is cut at about 200 characters and ends with `[+N chars]` (27 of 32 articles). | Remove that ending. |
| All 30 image links were `https`. | Still upgrade any `http` link, because cleartext is blocked on modern Android. |

## 3. Stack

Kotlin, Jetpack Compose with Material 3, Navigation Compose (type-safe routes), Hilt with KSP, Retrofit with kotlinx.serialization on OkHttp, Room, Coroutines and Flow, Coil for images, and a version catalog (`gradle/libs.versions.toml`).

Versions are chosen to build with compileSdk 36. The newest releases of Compose, Navigation, Lifecycle, Core, OkHttp and Coil need compileSdk 37, which is not installed on every machine.

## 4. Project structure

```
NewsArticles/
├── .github/workflows/
│   ├── ci.yml                    tests, lint, debug and release build on every push and pull request
│   └── release.yml               builds the APK and publishes a GitHub Release when a version tag is pushed
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── schemas/                  Room schema export (version 1)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/              strings, themes, icons, placeholder icon, backup rules
│       │   └── java/com/news/articles/
│       │       ├── NewsApp.kt
│       │       ├── MainActivity.kt
│       │       ├── core/mvi/               MviViewModel, CollectEffects
│       │       ├── di/                     AppModule, NetworkModule, DatabaseModule, RepositoryModule, Qualifiers
│       │       ├── domain/
│       │       │   ├── model/              Article, AppError, AppResult, SearchResult
│       │       │   ├── repository/         ArticleRepository
│       │       │   └── usecase/            ObserveTopHeadlines, RefreshTopHeadlines, SearchArticles, ObserveArticle
│       │       ├── data/
│       │       │   ├── remote/             NewsApi, ApiKeyInterceptor, SafeApiCall, dto/
│       │       │   ├── local/              NewsDatabase, ArticleDao, entity/
│       │       │   ├── mapper/             ArticleMappers
│       │       │   └── repository/         ArticleRepositoryImpl
│       │       └── presentation/
│       │           ├── articles/           ArticlesContract, ArticlesViewModel, ArticlesRoute, ArticlesScreen, components/
│       │           ├── details/            ArticleDetailsContract, ArticleDetailsViewModel, ArticleDetailsRoute, ArticleDetailsScreen
│       │           ├── common/             ArticleImage, StatusViews, AppErrorMessages
│       │           ├── model/              ArticleUi, ArticleDetailsUi
│       │           ├── navigation/         Destinations, NewsNavHost
│       │           └── theme/              Theme
│       └── test/java/com/news/articles/
│           ├── data/                       mapper, remote and repository tests
│           ├── presentation/               ViewModel tests for both screens
│           └── testutil/                   fakes, fixtures, MainDispatcherRule
├── gradle/                       version catalog and wrapper
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── PLAN.md
└── README.md
```

## 5. Data layer

### Network

- `NewsApi` has two calls on `v2/top-headlines`: one with `country` and `page`, one with `country` and `q`.
- `ApiKeyInterceptor` adds `x-api-key` to every request. The key comes from `BuildConfig`, which reads `local.properties` first and `gradle.properties` second. The header is redacted in debug logs.
- `safeApiCall` wraps every call and returns `AppResult.Success` or `AppResult.Failure(AppError)`. It always rethrows `CancellationException`.

| Cause | `AppError` |
|---|---|
| `SocketTimeoutException` (checked first, because it is also an `IOException`) | `Timeout` |
| Any other `IOException` | `NoInternet` |
| HTTP 401, or code `apiKeyInvalid`, `apiKeyMissing`, `apiKeyDisabled` | `Unauthorized` |
| HTTP 429, or code `rateLimited`, `apiKeyExhausted` | `RateLimited` |
| HTTP 500 and above | `Server` |
| Anything else, including unreadable JSON | `Unknown` |

### Room (offline mode)

Two tables:

- `articles`: `url` (primary key), title, description, author, image, source, published date, content, saved time.
- `top_headlines`: `url` (primary key and foreign key to `articles`), `position`.

Search results are saved in `articles` only. This lets the details screen open them, even after the process was killed, without changing the headlines list or its order.

DAO methods: `observeTopHeadlines` (a join ordered by position), `observeArticle`, `searchArticles` (`LIKE` on title and description), `upsertArticles` (`@Upsert`, never a replace, so the foreign key never cascades), and a `@Transaction` that replaces the headlines and removes saved articles older than 24 hours.

### Repository

```kotlin
override suspend fun refreshTopHeadlines(): AppResult<Unit> = withContext(ioDispatcher) {
    safeApiCall(json) {
        val responses = coroutineScope {
            TOP_HEADLINES_PAGES.map { page -> async { api.getTopHeadlines(COUNTRY, page) } }.awaitAll()
        }
        val articles = responses.flatMap { it.articles }.toEntities(savedAt = now)
        if (articles.isNotEmpty()) dao.replaceTopHeadlines(articles, staleThreshold)
    }
}
```

- Both pages load in parallel and are merged in page order.
- If either page fails, nothing is written, so the cache is never half replaced.
- If the server returns nothing usable, the saved headlines are kept.
- Search calls the API and saves the result. If the call fails and saved articles match, it returns those with `isFromCache = true`. If nothing matches, it returns the error.

## 6. MVI

`MviViewModel<Intent, State, Effect>` holds a `StateFlow` for state and a buffered `Channel` for effects.

### Main screen

```kotlin
data class ArticlesState(
    val query: String = "",
    val content: ArticlesContent = ArticlesContent.Loading,
    val isRefreshing: Boolean = false,
    val isSearching: Boolean = false,
    val isShowingSavedArticles: Boolean = false,
)

sealed interface ArticlesContent {
    data object Loading : ArticlesContent
    data class Items(val articles: ImmutableList<ArticleUi>) : ArticlesContent
    data class Empty(val query: String) : ArticlesContent
    data class Error(val error: AppError) : ArticlesContent
}

sealed interface ArticlesIntent {
    data class QueryChanged(val query: String) : ArticlesIntent
    data object ClearQuery : ArticlesIntent
    data object Refresh : ArticlesIntent
    data class ArticleClicked(val url: String) : ArticlesIntent
}

sealed interface ArticlesEffect {
    data class OpenDetails(val url: String) : ArticlesEffect
    data class ShowError(val error: AppError) : ArticlesEffect
}
```

`Refresh` means "reload what is on screen": it refreshes the headlines when the search is empty and repeats the search otherwise. The Retry button and pull to refresh both send it.

### Search pipeline

```kotlin
query
    .map { it.trim() }
    .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
    .distinctUntilChanged()
    .combine(searchRetries) { activeQuery, _ -> activeQuery }
    .collectLatest { activeQuery -> if (activeQuery.isEmpty()) showHeadlines() else search(activeQuery) }
```

- The text in the field updates straight away. Only the search is debounced.
- `collectLatest` cancels the old request when a new query arrives.
- The query is kept in `SavedStateHandle`.
- The first refresh runs once, from the ViewModel, never from a composable.
- While a new search runs, the previous results stay on screen and a thin progress bar shows.

### Details screen

- The route carries only the article link: `ArticleDetailsDestination(url)`.
- The ViewModel reads the link from `SavedStateHandle` and observes the article in Room. The state is `Loading`, `Content` or `NotFound`.
- Shows title, image, description and author ("Unknown author" if missing). It also shows source and date, the article text when it differs from the description, and a "Read full article" button.

## 7. What the user sees

| Case | Screen |
|---|---|
| First start, nothing saved | Full-screen loader |
| Refresh fails, nothing saved | Full-screen error with Retry |
| Refresh fails, articles saved | Saved list, banner "Showing saved articles" and a message bar with the reason |
| Search finds nothing | "No articles found for ..." |
| Search while offline | Matching saved articles and the banner |
| Search fails, nothing saved matches | Full-screen error with Retry |
| Article no longer saved | "This article is no longer available." |

## 8. Edge cases

Covered by unit tests:

- [x] Fast typing: debounce, and only the last query is searched
- [x] A slow old search can never replace a newer one
- [x] A blank query shows the headlines and makes no API call
- [x] `apple` and `apple ` make one call
- [x] Query is restored after process death, and limited to 100 characters
- [x] Duplicates across pages, `[Removed]` articles, missing links, non-web links
- [x] One page fails: the saved list is kept
- [x] Empty server response: the saved list is kept
- [x] 401, 429, 5xx, no internet, unreadable response, cancellation
- [x] `%` and `_` in an offline search
- [x] Missing article or missing link argument on the details screen

Built in, check by hand on a device:

- [ ] Missing or broken image shows the placeholder icon
- [ ] Long titles stop at three lines
- [ ] Rotation on both screens
- [ ] "Don't keep activities" on the details screen
- [ ] A fast double tap opens one details screen (`launchSingleTop`)
- [ ] Dark mode, large font size, TalkBack

## 9. Tests

71 unit tests, all passing. Fakes instead of mocks, `MockWebServer` for the real Retrofit setup, Turbine for effects, and `MainDispatcherRule` with virtual time.

| Class | Tests |
|---|---|
| `ArticlesViewModelTest` | 23 |
| `ArticleDetailsViewModelTest` | 7 |
| `ArticleRepositoryImplTest` | 18 |
| `ArticleMappersTest` | 13 |
| `SafeApiCallTest` | 10 |

To check that the ViewModel tests can really fail, four deliberate breaks were tried (no cancel of old searches, no `distinctUntilChanged`, no debounce, no error effect). Each one made the expected tests fail.

`toRoute()` could not be used in the details ViewModel, because it needs a real Android `Bundle` and fails in plain unit tests. The ViewModel reads the argument from `SavedStateHandle` by the property name instead.

## 10. CI and CD

- `ci.yml`: on pushes to `main` or `master`, on pull requests, and by hand. It runs the unit tests, lint, and the debug and release builds, then uploads the reports and the debug APK.
- `release.yml`: on a tag like `v1.0.0`. It runs the tests, builds the release APK, and publishes a GitHub Release with generated notes. Tags with a dash are pre-releases.
- Version name comes from the tag and the version code from the run number (`VERSION_NAME` and `VERSION_CODE`).
- Signing uses `SIGNING_*` environment variables when they exist. Otherwise the release build uses the debug key, so the APK still installs.
- Only official actions are used: `actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle` and `actions/upload-artifact`.

## 11. Build order and status

| # | Step | Status |
|---|---|---|
| 1 | Project set-up: version catalog, Hilt, Compose, Room, Retrofit, API key through BuildConfig | Done |
| 2 | Network layer, `AppError`, `safeApiCall` | Done |
| 3 | Room, repository, offline fallback | Done |
| 4 | Main screen: MVI, search, all states | Done |
| 5 | Details screen | Done |
| 6 | Unit tests (71) | Done |
| 7 | CI and CD workflows | Done |
| 8 | README and this plan | Done |
| 9 | Run on a device or emulator and check the list in section 8 | Not done: not run on a device yet |
| 10 | Check the first CI run on GitHub | Not done: the run has not been checked |

Checked locally: unit tests, lint (0 errors), debug build, release build with R8, release signing with and without a key, and the version properties.

## 12. Before you submit

- [ ] Run the app on a device or emulator and go through section 8
- [ ] Test with airplane mode on: headlines, search, and opening an article
- [ ] Check that the GitHub Actions run is green
- [ ] Add a few screenshots or a short screen recording to the README
- [ ] Make sure no personal API key is in the repository (`local.properties` is ignored)
- [ ] Send the repository link and an APK (from a GitHub Release or `assembleDebug`)
