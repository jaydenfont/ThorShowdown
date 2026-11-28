package com.example.thorshowdown

/**
 * Picks the app's [AppContainer]: the default one, unless another was installed before first use.
 * Once [container] has been read the choice is fixed, so a late install can't leave the app
 * half on one container and half on another.
 */
class AppGraph(
    private val defaultContainer: () -> AppContainer,
) {
    private var installed: AppContainer? = null
    private var chosen: AppContainer? = null

    /** The container in use; reading it fixes the choice. */
    val container: AppContainer
        @Synchronized get() = chosen ?: (installed ?: defaultContainer()).also { chosen = it }

    /** Uses [replacement] instead of the default. Throws if the container is already in use. */
    @Synchronized
    fun install(replacement: AppContainer) {
        check(chosen == null) { ALREADY_IN_USE }
        installed = replacement
    }

    private companion object {
        const val ALREADY_IN_USE = "Install a container before the app first uses one"
    }
}
