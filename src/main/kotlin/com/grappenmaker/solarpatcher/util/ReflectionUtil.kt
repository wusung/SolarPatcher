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

import java.lang.reflect.Field
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// Utility to make a property delegate itself to a java field (must be accessible)
fun <T, V> javaReflectionProperty(field: Field, receiver: T) = object : ReadWriteProperty<T, V> {
    @Suppress("UNCHECKED_CAST") // The cast is done safely and is handled, so we can suppress
    override fun getValue(thisRef: T, property: KProperty<*>): V =
        field[receiver] as? V
            ?: error("Invalid type given for reflection delegated field ${property.name}")

    override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
        field[receiver] = value
    }
}