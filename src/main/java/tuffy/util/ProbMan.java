package tuffy.util;

import java.util.Random;

public class ProbMan {
	private static Random rand = SeededRandom.getInstance();
	
	public static void resetStaticVars() {
		rand = SeededRandom.getInstance();
	}
	
	public static boolean testChance(double prob){
		return rand.nextDouble() < prob;
	}
	
	public static double nextDouble(){
		return rand.nextDouble();
	}
	
	public static boolean nextBoolean(){
		return rand.nextBoolean();
	}
}
