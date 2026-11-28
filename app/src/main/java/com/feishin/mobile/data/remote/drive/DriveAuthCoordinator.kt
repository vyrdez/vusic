package com.feishin.mobile.data.remote.drive

import android.accounts.Account
import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.feishin.mobile.BuildConfig
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DriveAuthResult {
    data class Authorized(
        val email: String,
        val accessToken: String,
        val expiresAtEpochMs: Long,
        val displayName: String? = null,
    ) : DriveAuthResult

    data class ResolutionRequired(
        val pendingIntent: PendingIntent,
        val email: String,
    ) : DriveAuthResult

    data object Cancelled : DriveAuthResult

    data class Unavailable(val reason: String) : DriveAuthResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : DriveAuthResult
}

sealed interface DriveRefreshOutcome {
    data class Refreshed(
        val accessToken: String,
        val expiresAtEpochMs: Long,
    ) : DriveRefreshOutcome

    data object ResolutionRequired : DriveRefreshOutcome

    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : DriveRefreshOutcome
}

fun interface DriveTokenRefresher {
    fun refreshTokenBlocking(accountEmail: String, failedToken: String): DriveRefreshOutcome
}

/**
 * Coordinates Google Sign-In via Android Credential Manager and Drive read-only
 * OAuth token acquisition/refresh via GMS [AuthorizationClient].
 */
@Singleton
class DriveAuthCoordinator internal constructor(
    private val appContext: Context,
    private val authorizationClientProvider: () -> AuthorizationClient,
    private val webClientIdProvider: () -> String,
    private val tokenClearer: (Context, String) -> Unit,
) : DriveTokenRefresher {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        appContext = context.applicationContext,
        authorizationClientProvider = { Identity.getAuthorizationClient(context.applicationContext) },
        webClientIdProvider = { BuildConfig.GOOGLE_WEB_CLIENT_ID },
        tokenClearer = { ctx, token -> GoogleAuthUtil.clearToken(ctx, token) },
    )

    private val authorizationClient: AuthorizationClient by lazy { authorizationClientProvider() }

    /**
     * Runs Credential Manager Sign-In with Google using [activityContext], then requests
     * `drive.readonly` authorization for the selected account.
     */
    suspend fun signInAndAuthorize(activityContext: Context): DriveAuthResult {
        val webClientId = webClientIdProvider().trim()
        if (webClientId.isBlank()) {
            return DriveAuthResult.Unavailable(
                "Google Drive sign-in is not configured (missing GOOGLE_WEB_CLIENT_ID).",
            )
        }

        val credential = try {
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            CredentialManager.create(activityContext).getCredential(
                context = activityContext,
                request = request,
            ).credential
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            return DriveAuthResult.Cancelled
        } catch (_: NoCredentialException) {
            return authorizeWithAccountPicker()
        } catch (_: GetCredentialProviderConfigurationException) {
            // Older Play Services cannot serve GetSignInWithGoogleOption; use the account picker.
            return authorizeWithAccountPicker()
        } catch (_: GetCredentialException) {
            return authorizeWithAccountPicker()
        } catch (e: GooglePlayServicesNotAvailableException) {
            return DriveAuthResult.Unavailable("Google Play Services is not available.")
        } catch (e: Exception) {
            return DriveAuthResult.Error(e.message ?: "Google sign-in failed.", e)
        }

        val googleIdToken = when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    } catch (e: GoogleIdTokenParsingException) {
                        return DriveAuthResult.Error("Invalid Google ID token response.", e)
                    }
                } else {
                    return DriveAuthResult.Error("Unsupported credential type returned by Google Sign-In.")
                }
            }
            else -> return DriveAuthResult.Error("Unsupported credential returned by Google Sign-In.")
        }

        val email = googleIdToken.id.trim()
        if (email.isBlank()) {
            return DriveAuthResult.Error("Google Sign-In did not return an account email.")
        }

        return authorizeAccount(
            email = email,
            displayName = googleIdToken.displayName?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Fallback when Credential Manager cannot return a Google ID token: GMS shows its own
     * account chooser + Drive consent via the returned [DriveAuthResult.ResolutionRequired].
     */
    suspend fun authorizeWithAccountPicker(): DriveAuthResult = withContext(Dispatchers.IO) {
        try {
            mapAuthorizationResult(
                result = awaitAuthorize("", includeEmailScope = true),
                fallbackEmail = "",
                displayName = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mapAuthorizationFailure(e)
        }
    }

    /**
     * Legacy token path (`GoogleAuthUtil`) used when the newer Authorization UI fails, e.g. on
     * older Play Services where the reauth step returns INTERNAL_ERROR. [emailHint] may be blank
     * when exactly one Google account is visible to the app (granted by the account chooser).
     */
    suspend fun authorizeLegacy(emailHint: String): DriveAuthResult = withContext(Dispatchers.IO) {
        val email = emailHint.trim().ifBlank { visibleGoogleAccountNames().singleOrNull().orEmpty() }
        if (email.isBlank()) return@withContext DriveAuthResult.Cancelled
        try {
            val token = GoogleAuthUtil.getToken(
                appContext,
                Account(email, GOOGLE_ACCOUNT_TYPE),
                "oauth2:$DRIVE_READONLY_SCOPE",
            ).trim()
            if (token.isBlank()) {
                DriveAuthResult.Error("Google Drive authorization returned an empty access token.")
            } else {
                DriveAuthResult.Authorized(
                    email = email,
                    accessToken = token,
                    expiresAtEpochMs = System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS,
                    displayName = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GooglePlayServicesAvailabilityException) {
            DriveAuthResult.Unavailable("Google Play Services is unavailable.")
        } catch (e: UserRecoverableAuthException) {
            val consent = e.intent
            if (consent == null) {
                DriveAuthResult.Error("Google Drive authorization requires consent, but no intent was provided.", e)
            } else {
                val pending = PendingIntent.getActivity(
                    appContext,
                    0,
                    consent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                DriveAuthResult.ResolutionRequired(pending, email)
            }
        } catch (e: GoogleAuthException) {
            DriveAuthResult.Error(e.message ?: "Google Drive authorization failed.", e)
        } catch (e: java.io.IOException) {
            DriveAuthResult.Error(e.message ?: "Network error while authorizing Google Drive.", e)
        } catch (e: Exception) {
            DriveAuthResult.Error(e.message ?: "Google Drive authorization failed.", e)
        }
    }

    private fun visibleGoogleAccountNames(): List<String> = runCatching {
        AccountManager.get(appContext).getAccountsByType(GOOGLE_ACCOUNT_TYPE).map { it.name }
    }.getOrDefault(emptyList())

    /** Requests `drive.readonly` OAuth access for a known Google [email]. */
    suspend fun authorizeAccount(
        email: String,
        displayName: String? = null,
    ): DriveAuthResult = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) {
            return@withContext DriveAuthResult.Error("Google account email is empty.")
        }
        try {
            val result = awaitAuthorize(normalizedEmail)
            mapAuthorizationResult(
                result = result,
                fallbackEmail = normalizedEmail,
                displayName = displayName,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mapAuthorizationFailure(e)
        }
    }

    /** Extracts a [DriveAuthResult] from an activity result returned by [DriveAuthResult.ResolutionRequired]. */
    fun resolveAuthorizationIntent(
        data: Intent?,
        fallbackEmail: String = "",
    ): DriveAuthResult {
        if (data == null) return DriveAuthResult.Cancelled
        return try {
            val result = authorizationClient.getAuthorizationResultFromIntent(data)
            mapAuthorizationResult(
                result = result,
                fallbackEmail = fallbackEmail.trim(),
                displayName = result.toGoogleSignInAccount()?.displayName?.trim()?.takeIf { it.isNotBlank() },
            )
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                DriveAuthResult.Cancelled
            } else {
                DriveAuthResult.Error(e.message ?: "Google Drive consent failed.", e)
            }
        } catch (e: GooglePlayServicesNotAvailableException) {
            DriveAuthResult.Unavailable("Google Play Services is not available.")
        } catch (e: Exception) {
            DriveAuthResult.Error(e.message ?: "Failed to process Google Drive authorization.", e)
        }
    }

    suspend fun refreshToken(accountEmail: String, failedToken: String): DriveRefreshOutcome =
        withContext(Dispatchers.IO) {
            refreshTokenBlocking(accountEmail, failedToken)
        }

    /**
     * Clears [failedToken] from GMS's local token cache and synchronously requests a fresh
     * access token (bounded to 10s). Must never be called on the main thread.
     */
    override fun refreshTokenBlocking(
        accountEmail: String,
        failedToken: String,
    ): DriveRefreshOutcome {
        val trimmedFailed = failedToken.trim()
        if (trimmedFailed.isNotBlank()) {
            try {
                tokenClearer(appContext, trimmedFailed)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return DriveRefreshOutcome.Failed("Interrupted while clearing stale token.", e)
            } catch (e: GooglePlayServicesNotAvailableException) {
                return DriveRefreshOutcome.Failed("Google Play Services is not available.", e)
            } catch (_: Exception) {
                // Proceed to authorize() so GMS can still mint a new token if cache clear failed.
            }
        }

        return try {
            val result = awaitAuthorize(accountEmail.trim())
            if (result.hasResolution()) {
                DriveRefreshOutcome.ResolutionRequired
            } else {
                val token = result.accessToken?.trim().orEmpty()
                if (token.isBlank()) {
                    DriveRefreshOutcome.Failed("Google Drive returned an empty access token.")
                } else {
                    DriveRefreshOutcome.Refreshed(
                        accessToken = token,
                        expiresAtEpochMs = System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS,
                    )
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            DriveRefreshOutcome.Failed("Interrupted while refreshing Google Drive token.", e)
        } catch (e: TimeoutException) {
            DriveRefreshOutcome.Failed("Timed out refreshing Google Drive token.", e)
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            DriveRefreshOutcome.Failed(cause.message ?: "Failed to refresh Google Drive token.", cause)
        } catch (e: Exception) {
            DriveRefreshOutcome.Failed(e.message ?: "Failed to refresh Google Drive token.", e)
        }
    }

    private fun awaitAuthorize(
        accountEmail: String,
        includeEmailScope: Boolean = false,
    ): AuthorizationResult {
        val scopes = buildList {
            add(Scope(DRIVE_READONLY_SCOPE))
            if (includeEmailScope) add(Scope("email"))
        }
        val builder = AuthorizationRequest.builder().setRequestedScopes(scopes)
        if (accountEmail.isNotBlank()) {
            builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        }
        return Tasks.await(
            authorizationClient.authorize(builder.build()),
            AUTH_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun mapAuthorizationResult(
        result: AuthorizationResult,
        fallbackEmail: String,
        displayName: String?,
    ): DriveAuthResult {
        val account = result.toGoogleSignInAccount()
        val resolvedEmail = account?.email?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackEmail.trim().takeIf { it.isNotBlank() }
            ?: visibleGoogleAccountNames().singleOrNull().orEmpty()
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            return if (pendingIntent != null) {
                DriveAuthResult.ResolutionRequired(pendingIntent, resolvedEmail)
            } else {
                DriveAuthResult.Error("Google Drive authorization requires user consent, but no intent was provided.")
            }
        }
        val token = result.accessToken?.trim().orEmpty()
        if (token.isBlank()) {
            return DriveAuthResult.Error("Google Drive authorization returned an empty access token.")
        }
        return DriveAuthResult.Authorized(
            email = resolvedEmail,
            accessToken = token,
            expiresAtEpochMs = System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS,
            displayName = displayName ?: account?.displayName?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun mapAuthorizationFailure(error: Throwable): DriveAuthResult {
        val root = (error as? ExecutionException)?.cause ?: error
        return when (root) {
            is GooglePlayServicesNotAvailableException,
            is GooglePlayServicesRepairableException,
            -> DriveAuthResult.Unavailable("Google Play Services is unavailable.")
            is ApiException -> if (root.statusCode == CommonStatusCodes.CANCELED) {
                DriveAuthResult.Cancelled
            } else {
                DriveAuthResult.Error(root.message ?: "Google Drive authorization failed.", root)
            }
            is TimeoutException -> DriveAuthResult.Error("Google Drive authorization timed out.", root)
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                DriveAuthResult.Cancelled
            }
            else -> DriveAuthResult.Error(root.message ?: "Google Drive authorization failed.", root)
        }
    }

    /** Fetches the authenticated user's email from Drive `about.get` when GMS omits it. */
    suspend fun fetchAboutEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) return@withContext null
        runCatching {
            val req = okhttp3.Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)")
                .header("Authorization", "Bearer $accessToken")
                .build()
            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val match = Regex(""""emailAddress"\s*:\s*"([^"]+)"""").find(body)
                match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val AUTH_TIMEOUT_SECONDS = 10L
        internal const val DEFAULT_TOKEN_TTL_MS = 55L * 60L * 1000L
    }
}
