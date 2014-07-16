package tuffy.util;

import java.util.Random;

public class SeededRandom {
	private static Random instance = null;

	protected SeededRandom() {
		// Exists only to defeat instantiation.
	}

	public static Random getInstance() {
		if (instance == null) {
			instance = new Random(1);
		}
		return instance;
	}
}
