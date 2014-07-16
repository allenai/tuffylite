package tuffy.infer.ds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
/**
 * A block of ground atoms. Exactly one atom in a block can be true.
 */
public class KeyBlock{

	public LinkedHashMap<Integer, ArrayList<GAtom>> keyConstraints = new LinkedHashMap<Integer, ArrayList<GAtom>>();
	
	public LinkedHashMap<GAtom, Integer> gatom2key = new LinkedHashMap<GAtom, Integer>();
	
	public void pushGAtom(Integer key, GAtom gatom){
		
		if(!keyConstraints.containsKey(key)){
			this.keyConstraints.put(key, new ArrayList<GAtom>());
		}
		this.keyConstraints.get(key).add(gatom);
		this.gatom2key.put(gatom, key);
	}
	
	public boolean hasKey(GAtom gatom){
		return this.gatom2key.containsKey(gatom);
	}
	
	public ArrayList<GAtom> getBlockMates(GAtom gatom){
		return this.keyConstraints.get(gatom2key.get(gatom));
	}
	
}
