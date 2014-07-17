package tuffy.sample;

import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap; // formerly java.util.concurrent.ConcurrentHashMap

import tuffy.util.Config;
import tuffy.util.myDouble;

public class SampleStatistic_WorldSumLogWeight extends MRFSampleStatistic{

	
	public SampleStatistic_WorldSumLogWeight(){
		this.type = StatisticType.WorldSumLogWeight;
	}
			
	Double logSumWeight = Double.NEGATIVE_INFINITY;
	
	@Override
	public void process(MRFSampleResult sampleWorld) {
		
		logSumWeight = Config.logAdd(logSumWeight, -sampleWorld.getCost());
		
		//System.out.println(logSumWeight + "\t" + -sampleWorld.getCost() + "\t" + sampleWorld.world);
		
		this.nProcessedSample ++;
		
	}

	@Override
	public Set getStatisticDomain() {
		return new LinkedHashSet<String>();
	}

	@Override
	public Double lookupStatistic(Object stat) {
		return logSumWeight;
	}

	@Override
	public void merge(Set<MRFSampleStatistic> results) {
		this.logSumWeight = Double.NEGATIVE_INFINITY;
		for(MRFSampleStatistic sampler_g : results){
			logSumWeight = Config.logAdd(logSumWeight, 
					sampler_g.lookupStatistic(null));
		}		
	}

}






