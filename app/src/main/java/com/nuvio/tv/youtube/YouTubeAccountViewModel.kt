package com.nuvio.tv.youtube

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class YouTubeAccountState(
    val signedIn: Boolean = false,
    val accountName: String? = null,
    /** The code to enter at [url] while a sign-in is waiting. */
    val code: String? = null,
    val url: String = "youtube.com/activate",
    val error: String? = null
)

/** Signs in to YouTube with the TV device-code flow, and signs out. */
@HiltViewModel
class YouTubeAccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private companion object { const val TAG = "YouTubeAccount" }

    private val _state = MutableStateFlow(YouTubeAccountState())
    val state: StateFlow<YouTubeAccountState> = _state.asStateFlow()

    private var signIn: Disposable? = null

    private val signInService by lazy {
        GlobalPreferences.instance(context)
        YouTubeServiceManager.instance().signInService
    }

    fun refresh() {
        // Reading accounts touches storage, so keep it off the main thread.
        Schedulers.io().scheduleDirect {
            val account = runCatching { signInService.selectedAccount }.getOrNull()
            val signed = runCatching { signInService.isSigned }.getOrDefault(false)
            val name = account?.let { it.name?.takeIf(String::isNotBlank) ?: it.email?.takeIf(String::isNotBlank) }
            _state.update { it.copy(signedIn = signed, accountName = name) }
        }
    }

    fun startSignIn() {
        signIn?.dispose()
        _state.update { it.copy(error = null, code = null) }
        signIn = signInService.signInObserve()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { code -> _state.update { it.copy(code = code) } },
                { e ->
                    Log.e(TAG, "sign-in failed", e)
                    _state.update { it.copy(code = null, error = e.message ?: "Couldn't start sign-in") }
                },
                {
                    // Approved on the other device.
                    _state.update { it.copy(code = null) }
                    refresh()
                }
            )
    }

    fun cancelSignIn() {
        signIn?.dispose()
        _state.update { it.copy(code = null) }
    }

    fun signOut() {
        Schedulers.io().scheduleDirect {
            runCatching { signInService.selectedAccount?.let(signInService::removeAccount) }
                .onFailure { Log.e(TAG, "sign-out failed", it) }
            YouTubeServiceManager.instance().invalidateCache()
            refresh()
        }
    }

    override fun onCleared() {
        signIn?.dispose()
    }
}
