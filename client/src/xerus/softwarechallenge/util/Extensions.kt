@file:Suppress("NOTHING_TO_INLINE")

package xerus.softwarechallenge.util

import sc.plugin2018.*

typealias F = JvmField

inline fun Field.isType(type: FieldType) = this.type == type

fun FieldType.isNot(vararg types: FieldType) =
		!types.any { this == it }

inline fun MutableCollection<Move>.addMove(vararg actions: Action) = add(Move(*actions))

fun Move.add(action: Action) = also { it.actions.add(action) }


fun Collection<Move>.str(): String = joinToString(prefix = "| ", separator = "\n| ", transform = { it.str() })

fun Move.str() = "Move[${actions.joinToString { it.str() }}]"

fun Action.str() = when (this) {
	is Advance -> "Advance$distance"
	is Card -> "$type${if(value != 0) value.toString() else ""}"
	is ExchangeCarrots -> "Exchange$value"
	else -> toString().substringBefore(" order")
}

