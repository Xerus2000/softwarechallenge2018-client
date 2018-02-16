package xerus.softwarechallenge.logic2018

import sc.plugin2018.Advance
import sc.plugin2018.Card
import sc.plugin2018.CardType
import sc.plugin2018.ExchangeCarrots
import sc.plugin2018.FallBack
import sc.plugin2018.FieldType
import sc.plugin2018.GameState
import sc.plugin2018.Move
import sc.plugin2018.util.Constants
import xerus.softwarechallenge.Starter
import java.util.ArrayList

class Jumper1(client: Starter, params: String, debug: Int): LogicBase(client, params, debug, "v0.1") {

    override fun findMoves(state: GameState): Collection<Move> {
        val possibleMoves = state.possibleMoves // Enthält mindestens ein Element
        val winningMoves = ArrayList<Move>()
        val saladMoves = ArrayList<Move>()
        val selectedMoves = ArrayList<Move>()
        val currentPlayer = state.currentPlayer
        val fieldindex = currentPlayer.fieldIndex
        for (move in possibleMoves) {
            for (action in move.actions) {
                if (action is Advance) {
                    when {
                        action.distance+fieldindex == Constants.NUM_FIELDS-1
                        -> winningMoves.add(move) // Zug ins Ziel
                        state.board.getTypeAt(action.distance+fieldindex) == FieldType.SALAD
                        -> saladMoves.add(move) // Zug auf Salatfeld
                        else -> selectedMoves.add(move)
                    }
                } else if (action is Card) {
                    if (action.type==CardType.EAT_SALAD) {
                        // Zug auf Hasenfeld und danach Salatkarte
                        saladMoves.add(move)
                    } // Muss nicht zusaetzlich ausgewaehlt werden, wurde schon durch Advance ausgewaehlt
                } else if (action is ExchangeCarrots) {
                    if (action.value==10 && currentPlayer.carrots<30 && fieldindex<40 && currentPlayer.lastNonSkipAction !is ExchangeCarrots) {
                        // Nehme nur Karotten auf, wenn weniger als 30 und nur am Anfang und nicht zwei mal hintereinander
                        selectedMoves.add(move)
                    } else if (action.value==-10 && currentPlayer.carrots>30 && fieldindex>=40) {
                        // abgeben von Karotten ist nur am Ende sinnvoll
                        selectedMoves.add(move)
                    }
                } else if (action is FallBack) {
                    if (fieldindex > 56) {
                        if (currentPlayer.salads > 0)
                            selectedMoves.add(move)
                    } else if (fieldindex - state.getPreviousFieldByType(FieldType.HEDGEHOG, fieldindex)<5) {
                        selectedMoves.add(move)
                    }
                } else {
                    // Salatessen oder Skip
                    selectedMoves.add(move)
                }
            }
        }
        return when {
            winningMoves.isNotEmpty() -> winningMoves
            saladMoves.isNotEmpty() -> saladMoves
            selectedMoves.isNotEmpty() -> selectedMoves
            else -> possibleMoves
        }
    }

}