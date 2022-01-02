package com.grappenmaker.solarpatcher.asm

// Utility to match on methods
interface MethodMatcher {
    fun matches(other: MethodDescription): Boolean
}

object MatchAny : MethodMatcher {
    override fun matches(other: MethodDescription) = true
}

class MatchDescription(private val desc: MethodDescription) : MethodMatcher {
    override fun matches(other: MethodDescription) = desc.match(other)
}