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

import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.modules.MethodInfo
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

private const val msg = "has not been loaded yet; check the load order!"

// ONLY USE WHEN YOU'RE ALMOST 100 PERCENT SURE IT WILL SUCCEED!
fun MethodDescription?.ensure() = this ?: error("Method description $msg")
fun MethodInfo?.ensure() = this ?: error("Method info $msg")
fun MethodNode?.ensure() = this ?: error("Method node $msg")
fun ClassNode?.ensure() = this ?: error("Class info $msg")