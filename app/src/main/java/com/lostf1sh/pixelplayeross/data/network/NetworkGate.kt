package com.lostf1sh.pixelplayeross.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide switch for everything that leaves the device.
 *
 * PixelPlayer is a local music player first, so online features (Navidrome / Jellyfin,
 * lyrics lookups, ListenBrainz, update checks) stay off until the user opts in. The flag
 * lives in DataStore, but every OkHttp request needs to read it synchronously, so
 * [PixelPlayerApplication] mirrors it into this object once at startup.
 */
object NetworkGate {
    private val enabled = AtomicBoolean(false)
    private val loaded = AtomicBoolean(false)

    /**
     * True while online features are allowed. Before the stored preference has been read we
     * fail open: a request during the first few milliseconds of process start must not be
     * blocked just because the mirror was not primed yet.
     */
    fun isEnabled(): Boolean = !loaded.get() || enabled.get()

    fun setEnabled(value: Boolean) {
        enabled.set(value)
        loaded.set(true)
    }

    /** Test seam. */
    fun reset() {
        enabled.set(false)
        loaded.set(false)
    }

    fun bind(scope: CoroutineScope, enabledFlow: Flow<Boolean>) {
        scope.launch {
            enabledFlow.collect { setEnabled(it) }
        }
    }
}

/** Thrown by [NetworkAccessInterceptor] so callers take their normal failure path. */
class NetworkDisabledException :
    IOException("Network features are disabled in PixelPlayer settings")

/**
 * Fails every non-loopback request fast while [NetworkGate] is off.
 *
 * Loopback is always allowed: the stream proxies (Navidrome, Jellyfin, cloud) hand ExoPlayer
 * a `http://127.0.0.1:port/...` URL, and blocking those would break playback of media the
 * player already has locally.
 */
class NetworkAccessInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (NetworkGate.isEnabled() || isLoopback(request.url)) {
            return chain.proceed(request)
        }
        throw NetworkDisabledException()
    }

    private fun isLoopback(url: HttpUrl): Boolean = when (url.host) {
        "localhost", "127.0.0.1", "::1" -> true
        else -> false
    }
}
