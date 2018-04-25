@file:Suppress("NOTHING_TO_INLINE")

package xerus.softwarechallenge.util

import sc.plugin2018.Action
import sc.plugin2018.Field
import sc.plugin2018.FieldType
import sc.plugin2018.Move

inline fun Field.isType(type: FieldType) = this.type == type

inline fun MutableCollection<Move>.addMove(vararg actions: Action) = add(Move(*actions))

fun Move.add(action: Action) = also { it.actions.add(action) }


fun Collection<Move>.str(): String = joinToString(prefix = "| ", separator = "\n| ", transform = { it.str() })

fun Move.str() = "Move[${actions.joinToString { it.str() }}]"

fun Action.str() = toString().substringBefore(" order")

