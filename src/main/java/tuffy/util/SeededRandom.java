package tuffy.util;

import java.util.Random;

public class SeededRandom {
	private static Random instance = null;

	protected SeededRandom() {
		// Exists only to defeat instantiation.
	}

	public static Random getInstance() {
		if (instance == null) {
			if (Config.seed != 0) {
				instance = new Random(Config.seed);
			} else {
				instance = new Random();
			}
		}
		return instance;
	}
}
