package com.tabletopcontrol.core.persistence

import java.io.File

/**
 * Resolves application config paths under the user's `~/.tabletopcontrol` directory.
 */
object AppConfigPaths {

    /** Returns `~/.tabletopcontrol`, creating it if missing. */
    fun configDir(): File = File(System.getProperty("user.home"), ".tabletopcontrol").also { it.mkdirs() }

    /** Returns a config file inside [configDir], creating the base directory if needed. */
    fun configFile(name: String): File = File(configDir(), name)

    /** Returns a subdirectory inside [configDir], creating it if missing. */
    fun configSubDir(name: String): File = File(configDir(), name).also { it.mkdirs() }
}
