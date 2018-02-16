package xerus.softwarechallenge.util;

import java.security.SecureRandom;
import java.util.*;

import javax.swing.JFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.plugin2018.*;
import sc.shared.GameResult;
import sc.shared.InvalidMoveException;
import sc.shared.PlayerColor;
import sc.shared.PlayerScore;
import xerus.softwarechallenge.Starter;
import xerus.util.Timer;
import xerus.util.swing.table.MyTable;
import xerus.util.swing.table.ScrollableJTable;
import xerus.util.tools.StringTools;

/** schafft Grundlagen fuer eine Logik */
public abstract class LogicHandler extends Timer implements IGameHandler {

	protected final Logger log;
	protected GameState currentGameState;

	private Starter client;

	public LogicHandler(Starter client, String params, int debug, String version) {
		this.client = client;
		log = LoggerFactory.getLogger(this.getClass());
		log.warn(version + " - Parameter: " + params);
		if (debug == 2) {
			((ch.qos.logback.classic.Logger) log).setLevel(ch.qos.logback.classic.Level.DEBUG);
			log.info("Debug enabled");
		} else if (debug == 1) {
			((ch.qos.logback.classic.Logger) log).setLevel(ch.qos.logback.classic.Level.INFO);
			log.info("Info enabled");
		}
		this.params = !params.isEmpty() ? StringTools.split(params) : defaultParams();
	}

	@Override
	public void onRequestAction() {
		start();
		gueltig = 0;
		ungueltig = 0;
		lastdepth = 0;
		Move move = null;

		try {
			move = findBestMove();
			if (move == null || testmove(currentGameState, move) == null) {
				log.info("Kein gueltiger Move gefunden! Suche Simplemove...");
				move = simplemove();
			}
		} catch (CloneNotSupportedException e) {
			log.error("Fehler beim klonen!\n");
			e.printStackTrace();
		}

		sendAction(move);
		log.info(String.format("Zeit: %sms moves: %s/%s Kalkulationstiefe: %s Genutzt: %s", runtime(), gueltig, ungueltig, depth, lastdepth));
	}

	protected Move findBestMove() throws CloneNotSupportedException {
		return breitensuche();
	}

	/** ermittelt die Wertigkeit der gegebenen Situation<br>
	 * @return Bewertung der gegebenen Situation */
	protected abstract double evaluate(GameState state);

	protected double[] params;

	/** Die Standard-Parameter - gibt in der Basisimplementierung ein leeres Array zur�ck */
	protected double[] defaultParams() {
		return new double[]{};
	}

	protected int depth;
	private int lastdepth;

	/** sucht den besten Move per breitensuche basierend auf dem aktuellen GameState
	 * @return bester gefundener Move */
	protected Move breitensuche() throws CloneNotSupportedException {
		// Variablen vorbereiten
		Queue<Node> queue = new LinkedList();
		depth = 0;
		MP bestmove = new MP();
		GameState nodestate;
		Node node;
		Collection<Move> moves;

		// Queue f�llen
		initplayer();
		moves = findMoves(currentGameState);
		if (log.isDebugEnabled())
			log.debug("Gefundene Zuege:\n{}", tostring(moves));
		if (moves.size() == 1)
			return moves.iterator().next();

		for (Move move : moves) {
			GameState newstate = testmove(currentGameState, move);
			if (newstate == null)
				continue;
			if (gewonnen(newstate))
				return move;
			Node newnode = new Node(newstate, move);
			queue.add(newnode);
			bestmove.update(move, evaluate(newstate));
		}
		// Breitensuche
		breitensuche: while (depth < 7 && runtime() < 1700) {
			if ((node = queue.poll()) == null)
				break;
			depth = node.depth;
			nodestate = node.gamestate;
			initplayer(nodestate);
			findMoves(nodestate);
			// sinnlosen Zug ausschliessen
			if (moves.size() == 0)
				continue;
			for (Move move : moves) {
				if (runtime() > 1750)
					break breitensuche;
				initplayer(nodestate);
				GameState newstate = testmove(nodestate, move);
				if (newstate == null)
					continue;
				Node newnode = node.update(newstate);
				double points = evaluate(node.gamestate) + node.bonus - node.depth;
				// Aussortieren
				if (points + 5 < bestmove.points)
					continue;
				queue.add(newnode);
				// Aktualisierung der Bestpunktzahl
				if (bestmove.update(node.move, points)) {
					lastdepth = depth;
					if (log.isDebugEnabled())
						log.debug(String.format("Neuer bester Zug bei Tiefe %s: %s Punkte %s - %s", depth, tostring(node.move), points, tostring(nodestate.getCurrentPlayer())));
				}
			}
		}
		if (evaluate(currentGameState) > bestmove.points)
			log.warn("Bin wahrscheinlich in Sackgasse!");
		return bestmove.obj;
	}

	/** stellt m�gliche Moves zusammen basierend auf dem gegebenen GameState<br>
	 * Diese Methode wird f�r die Tiefensuche genutzt<br>
	 * �berschreiben ist
	 * @param state gegebener GameState
	 * @return ArrayList mit gefundenen Moves */
	protected Collection<Move> findMoves(GameState state) throws CloneNotSupportedException {
		throw new UnsupportedOperationException("Es wurde keine Methode f�r das ermitteln der moves f�r die Tiefensuche definiert!");
	}

	protected static final Random rand = new SecureRandom();

	private class Node {

		public GameState gamestate;
		public Move move;
		public int depth;
		public double bonus;

		/** erstellt eine neue Node mit dem gegebenen GameState und Move ohne bonus */
		public Node(GameState state, Move m) {
			this(state, m, 0);
		}

		/** erstellt eine neue Node mit dem gegebenen GameState und Move mit bonus */
		public Node(GameState state, Move m, double bonus) {
			this(state, m, bonus, 1);
		}

		private Node(GameState state, Move m, double b, int d) {
			move = m;
			gamestate = state;
			depth = d;
			bonus = b;
		}

		/** gibt eine neue Node zur�ck mit dem neuen GameState und einer um 1 erh�hten Tiefe
		 * @param s der neue GameState
		 * @return neue Node mit Ver�nderungen */
		public Node update(GameState s) {
			return new Node(s, move, bonus, depth + 1);
		}

	}

	// GRUNDLAGEN

	@Override
	public void sendAction(Move move) {
		if (move != null)
			log.debug("Sende {}\n", tostring(move));
		else {
			log.warn("Kein Zug mehr m�glich!");
			client.sendMove(move());
		}
		move.setOrderInActions();
		client.sendMove(move);
	}

	protected void initplayer() {
	}

	@SuppressWarnings("unused")
	protected void initplayer(GameState nodestate) {
	}

	PlayerColor myColor;

	@Override
	public void onUpdate(GameState state) {
		currentGameState = state;
		Player dran = state.getCurrentPlayer();
		if (myColor == null && client.getColor() != null) {
			//display(state);
			myColor = client.getColor();
			log.info("Ich bin {}", myColor);
		}
		log.info("Zug: {} Dran: {} - " + tostring(dran), state.getTurn(), isme(dran.getPlayerColor()));
	}

	public static void display(GameState state) {
		JFrame frame = new JFrame();
		String fieldString = state.getBoard().toString();
		MyTable table = new ScrollableJTable("Index", "Field").addToComponent(frame, null);
		String[] fields = fieldString.split("index \\d+");
		for (int i = 1; i < fields.length - 1; i++) {
			table.addRow(i + "", fields[i]);
		}
		table.fitColumns(0);
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	public void onUpdate(Player arg0, Player arg1) {
	}

	private String isme(PlayerColor color) {
		return color == myColor ? "ich" : "nicht ich";
	}

	protected String tostring(Collection<Move> moves) {
		String out = "";
		for (Move m : moves)
			out += "| " + tostring(m) + "\n";
		return out;
	}

	protected String tostring(Move m) {
		String out = "Zug: ";
		for (Action a : m.actions)
			out += a.toString() + ", ";
		return out.substring(0, out.length() - 2);
	}

	protected abstract String tostring(Player player);

	// Zugmethoden

	protected Move move(Action... actions) {
		return new Move(Arrays.asList(actions));
	}

	protected boolean perform(Action a, GameState s) {
		try {
			a.perform(s);
			return true;
		} catch (InvalidMoveException e) {
			return false;
		}
	}

	// Feldmethoden

	protected boolean fieldistype(Field field, FieldType type) {
		return field.getType().equals(type);
	}

	protected abstract boolean gewonnen(GameState state);

	int gueltig;
	int ungueltig;

	/** testet einen Move mit dem gegebenen GameState
	 * @param state gegebener State
	 * @param m der zu testende Move
	 * @return null, wenn der Move fehlerhaft ist, sonst den GameState nach dem Move
	 * @throws CloneNotSupportedException */
	protected GameState testmove(GameState state, Move m) {
		GameState newstate = clone(state);
		try {
			m.setOrderInActions();
			m.perform(newstate);
			gueltig++;
			return newstate;
		} catch (InvalidMoveException e) {
			ungueltig++;
			if (log.isDebugEnabled()) {
				String message = e.getMessage();
				// if(!message.equals("Die maximale Geschwindigkeit von 6 darf nicht überschritten werden."))
				log.info("FEHLERHAFTER ZUG: {} - {} FEHLER: " + message, tostring(state.getCurrentPlayer()), tostring(m));
			}
		}
		return null;
	}

	protected abstract Move simplemove() throws CloneNotSupportedException;

	protected GameState clone(GameState s) {
		try {
			return s.clone();
		} catch (CloneNotSupportedException e) {
			log.error("Konnte GameState nicht klonen!");
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void gameEnded(GameResult data, PlayerColor color, String errorMessage) {
		List<PlayerScore> scores = data.getScores();
		String cause = String.format("Ich %s Gegner %s", scores.get(color.ordinal()).getCause(), scores.get(color.opponent().ordinal()).getCause());
		if (data.getWinners().isEmpty()) {
			log.warn("Kein Gewinner! Grund: {}", cause);
			// System.exit(0);
		}
		PlayerColor winner = ((Player) data.getWinners().get(0)).getPlayerColor();
		int myscore = getScore(scores, color);
		if (data.isRegular())
			log.warn(String.format("Spiel beendet! Gewinner: %s Punkte: %s Gegner: %s", isme(winner), myscore, getScore(scores, color.opponent())));
		else
			log.warn(String.format("Spiel unregulaer beendet! Punkte: %s Grund: %s", myscore, cause));
		// System.exit((color == winner ? 100 : 0) + myscore);
	}

	private static int getScore(List<PlayerScore> scores, PlayerColor color) {
		return scores.get(color.ordinal()).getValues().get(1).intValue();
	}

}
