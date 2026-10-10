# News Articles

A small Android app that shows US top headlines from [NewsAPI](https://newsapi.org). You can search the articles, open one to read its details, and keep using the app without internet.

Built with Kotlin, Jetpack Compose, MVI, Hilt, Retrofit, Coroutines and Flow, and Room.

## Features

- **Main screen**: a search field at the top and a list of articles below it. Each article shows an image and a title.
- **Headlines**: the app loads `top-headlines?country=us&page=1` and `page=2` at the same time and merges them into one list. Duplicates are removed.
- **Search**: results appear as you type, after a short pause (400 ms). Old requests are cancelled, so a slow answer can never replace a newer one. An empty search shows the headlines again.
- **Details screen**: title, image, description, author, source, date and a button to open the full article in the browser.
- **Offline mode**: articles are saved in Room. With no internet the app shows the saved headlines, and search falls back to the saved articles.
- **Error handling**: no internet, timeout, invalid key, rate limit, server error and unreadable response each show a clear message with a Retry button, or a message bar when saved articles can still be shown.
- **Extras**: pull to refresh, dark mode, dynamic colours on Android 12+, edge-to-edge layout.
- **Animations**: slide and fade between the two screens, a staggered entrance and a bouncy press effect on the article cards, and a staggered reveal of the text on the details screen. They follow the system setting for animation speed.

Every request sends the `x-api-key` header.

## Architecture

The app has three layers and follows MVI in the presentation layer.

```
Compose UI --Intent--> ViewModel --> Use cases --> Repository --> Room (source of truth)
    ^                      |                            |
    |------ State ---------|                            +--> Retrofit (NewsAPI)
    |------ Effect (one-off: navigate, snackbar)
```

- **State**: one immutable object per screen, held in a `StateFlow`. Screen content is a sealed type (`Loading`, `Items`, `Empty`, `Error`), so impossible combinations cannot happen.
- **Intent**: everything the user does goes through `onIntent()`.
- **Effect**: one-off events (open a screen, show a message) are sent through a `Channel` and collected only while the screen is started.
- **Domain** has plain Kotlin models, the repository interface and small use cases. It does not know about Android, Room or Retrofit.
- **Data** has the Retrofit API, the Room database, mappers and the repository.

### Decisions

| Topic | Decision |
|---|---|
| Source of truth | The headlines list is always read from Room. The network only refreshes it. |
| Refresh | All or nothing. If either page fails, the saved list is left as it was. An empty response also keeps the saved list. |
| Search results | Saved in the `articles` table only, so the details screen can open them, but they never change the headlines list. Saved articles that are no longer headlines are removed after 24 hours. |
| Search | Debounce, `distinctUntilChanged` on the trimmed text, and `collectLatest` to cancel the previous request. The text is stored in `SavedStateHandle`, so it survives process death. Maximum length is 100 characters. |
| Offline search | Falls back to a `LIKE` search on saved articles. `%` and `_` typed by the user are escaped. |
| Errors | `safeApiCall` turns exceptions and API error codes into a sealed `AppError`. Cancellation is always rethrown. The UI maps each error to a string resource. |
| Navigation | Navigation Compose with type-safe routes. Only the article link is passed, and the details screen reads the article from Room. This also works after process death. |
| Bad data | Articles with no title or link, `[Removed]` articles, non-web links and author fields that are links are cleaned or dropped. `http` image links are upgraded to `https`. |
| API key | Added by an OkHttp interceptor, read from `BuildConfig`, never logged. The key is not sent to image servers, because Coil uses its own client. |

## Project structure

```
app/src/main/java/com/news/articles
├── core/mvi            MviViewModel, CollectEffects
├── di                  Hilt modules
├── domain              model, repository interface, use cases
├── data
│   ├── remote          NewsApi, interceptor, safeApiCall, DTOs
│   ├── local           Room database, DAO, entities
│   ├── mapper          DTO and entity mapping
│   └── repository      ArticleRepositoryImpl
└── presentation
    ├── articles        contract, ViewModel, route, screen, components
    ├── details         contract, ViewModel, route, screen
    ├── common          images, status views, error messages
    ├── model           UI models
    ├── navigation      routes and NavHost
    └── theme
```

A longer description is in [PLAN.md](PLAN.md).

## Getting started

You need a recent Android Studio that supports Android Gradle Plugin 9.4, JDK 17 or newer (the one bundled with Android Studio is fine), and Android SDK platform 36.

1. Open the project in Android Studio and let Gradle sync.
2. Run the `app` configuration on a device or emulator (Android 8.0 or newer).

### API key

`gradle.properties` holds the key from the task as the default `NEWS_API_KEY`. To use your own key, add this line to `local.properties`, which is not committed:

```
NEWS_API_KEY=your_key_here
```

Do not commit your own key.

### Commands

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

## Tests

There are 71 unit tests. They use fakes instead of mocks, and `MockWebServer` for the real Retrofit setup.

| Class | Tests | What it covers |
|---|---|---|
| `ArticlesViewModelTest` | 23 | Loading, saved headlines, refresh errors, debounce, quick typing, latest search wins, blank query, empty and error results, saved-article flag, clear, refresh, saved state, click effect |
| `ArticleDetailsViewModelTest` | 7 | Loading, found, not found, missing argument, live updates, open link effect |
| `ArticleRepositoryImplTest` | 18 | Both pages with the key header, merge order, duplicates, bad articles, failed page keeps the cache, error mapping, search, offline search fallback, wildcard escaping |
| `ArticleMappersTest` | 13 | Cleaning, trimming, dropped articles, dates, links, images |
| `SafeApiCallTest` | 10 | Every error type, API error codes, cancellation |

## CI and CD

Both workflows are in `.github/workflows`.

- **CI** (`ci.yml`) runs on pushes to `main` or `master`, on pull requests, and by hand. It runs the unit tests and lint, builds the debug and release variants (so R8 is checked), and uploads the test and lint reports and the debug APK.
- **Release** (`release.yml`) runs when a tag like `v1.0.0` is pushed. It runs the tests, builds the release APK, and publishes a GitHub Release with the APK attached and generated notes. Tags with a dash, such as `v1.0.0-rc1`, are marked as pre-releases. The version name comes from the tag and the version code from the run number.

### Signing

Without any setup, the release APK is signed with the debug key. It installs on any device, but it must not be used for the Play Store. To sign with your own key, add these repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Your keystore file as base64, from `base64 -i release.jks` |
| `SIGNING_STORE_PASSWORD` | The keystore password |
| `SIGNING_KEY_ALIAS` | The key alias |
| `SIGNING_KEY_PASSWORD` | The key password |

The same values work locally as environment variables. Use `SIGNING_KEYSTORE_PATH` for the path of the keystore file instead of `KEYSTORE_BASE64`.

## Known limits and next steps

- Only the two pages the task asks for are loaded, with no paging. Paging 3 with a `RemoteMediator` would be the next step.
- Search loads the first page of results only.
- There are no UI tests yet. Compose tests for the two screens would be the next addition.
- It is one Gradle module. Splitting it by layer makes sense only when the app grows.
- Library versions are chosen to build with compileSdk 36. The newest releases need compileSdk 37.
