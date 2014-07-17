package tuffy.sample;

import java.util.BitSet;
import java.util.LinkedHashMap; // formerly java.util.concurrent.ConcurrentHashMap

import tuffy.infer.MRF;
import tuffy.util.DeterministicMapHelper;
import tuffy.util.myDouble;

public class MRFSampleResult {

	MRF mrf;
	BitSet world;
	
	public MRFSampleResult(MRF _mrf, BitSet _world){
		mrf = _mrf;
		world = _world;
	}
	
	public double getCost(){
		return this.mrf.getCost(world);
	}
	
	public LinkedHashMap<String, myDouble> getClauseViolations(){
		
		LinkedHashMap<String, myDouble> rs = new 
				LinkedHashMap<String, myDouble>();
		
		Integer[] tallies = this.mrf.getClauseTallies(world);
		for(int i=0;i<tallies.length;i++){
			for(String ffcid : (String[]) this.mrf.clauseToFFCID[i]){
				DeterministicMapHelper.putIfAbsent(rs, ffcid, new myDouble(0));
				rs.get(ffcid).tallyDouble(tallies[i]);
			}
		}
		
		return rs;
		
	}
	
	
	
	
}
