package tuffy.util;

import java.util.LinkedHashMap;

public class DeterministicMapHelper {
	public static void putIfAbsent(LinkedHashMap map, Object k, Object v) {
		if (!map.containsKey(k)) {
			map.put(k, v);
		}
	}
}
