/*
 * Solar Patcher, a runtime patcher for Lunar Client
 * Copyright (C) 2022 Solar Tweaks and respective contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.grappenmaker.solarpatcher.util

import java.lang.instrument.ClassFileTransformer
import java.net.URLClassLoader
import java.security.ProtectionDomain

// Utility to get the lunar client main classloader on runtime
object LunarClassLoader : ClassFileTransformer {
    // Feel free to use !! operator, because if this does not exist lc is a lie :)
    // Not lateinit because there needs to be a nullcheck
    var loader: ClassLoader? = null
        private set

    override fun transform(
        loader: ClassLoader?,
        className: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain,
        classfileBuffer: ByteArray
    ): ByteArray? {
        if (this.loader == null && loader != null && URLClassLoader::class.java.isAssignableFrom(loader::class.java)) {
            this.loader = loader
        }

        return null
    }
}