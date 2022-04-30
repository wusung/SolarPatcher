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

package com.grappenmaker.solarpatcher.util.generation

object Instances {
    val utility by lazy { instance<IUtility>(utilityClass) }
    val chatUtility by lazy { instance<IChatUtility>(chatUtilityClass) }
    val configDelegate by lazy { instance<IConfigDelegate>(configDelegateClass) }
    val playerItemsLayer by lazy { instance<Any>(playerItemsLayerClass) }

    private inline fun <reified T> instance(clazz: Class<*>): T =
        clazz.getConstructor().newInstance() as T
}