package tuffy.infer;

import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.LinkedHashMap; // formerly java.util.concurrent.ConcurrentHashMap

import tuffy.infer.ds.GAtom;
import tuffy.infer.ds.GClause;
import tuffy.infer.ds.KeyBlock;
//import tuffy.learn.Learner;
import tuffy.mln.Clause;
import tuffy.mln.MarkovLogicNetwork;
//import tuffy.sample.DS_JunctionTree;
//import tuffy.sample.SampleAlgorithm_MCSAT;
//import tuffy.sample.SampleAlgorithm_MetropolisHastingsSampling;
//import tuffy.sample.SampleAlgorithm_NaiveSampling;
//import tuffy.sample.SampleAlgorithm_SerialMixture;
import tuffy.util.BitSetIntPair;
import tuffy.util.Config;
import tuffy.util.DebugMan;
import tuffy.util.ExceptionMan;
import tuffy.util.HashArray;
import tuffy.util.SeededRandom;
import tuffy.util.Stats;
import tuffy.util.Timer;
import tuffy.util.UIMan;
import tuffy.util.myInt;
//import tuffy.worker.MLEWorker;
//import tuffy.worker.MLEWorker_gibbsSampler;
//import tuffy.worker.MLEWorkerInstance;
import tuffy.worker.ds.MLEWorld;

/**
 * In-memory data structure representing an MRF.
 * 
 */
public class MRF {

	public static enum INIT_STRATEGY {ALL_FALSE, COIN_FLIP, GREEDY, COPY_LOW, TRAINING_DATA, NO_CHANGE};
	public INIT_STRATEGY initStrategy = INIT_STRATEGY.COIN_FLIP;

	public INIT_STRATEGY getInitStrategy(){
		return initStrategy;
	}

	public void setInitStrategy(INIT_STRATEGY strategy){
		initStrategy = strategy;
	}

	public KeyBlock keyBlock = new KeyBlock();

	public boolean isInfered = false;

	public synchronized boolean getInferAndFlipIfIsFalse() {
		if(isInfered == false){
			isInfered = true;
			return false;
		}else{
			return true;
		}
	} 

	/**
	 * Map from GAtom ID to GAtom object.
	 */
	public LinkedHashMap<Integer, GAtom> atoms = new LinkedHashMap<Integer, GAtom>();

	public boolean ownsAllAtoms = false;

	// TODO: blocking - which is???
//	private boolean usingBlocks = true;

	private LinkedHashSet<Integer> coreAtoms = new LinkedHashSet<Integer>();

	/**
	 * Array of all GClause objects in this MRF.
	 */
	public ArrayList<GClause> clauses = new ArrayList<GClause>();
	

	// TODO(ericgribkoff) Need ArrayList of atom IDs to select a random atom
	// for simulated annealing step of SampleSAT.
	public ArrayList<Integer> atomsArrayList = new ArrayList<Integer>();
	
	// Clauses index and atomid for the current subset of clauses selected by
	// MC-SAT for passing to SampleSAT
	public ArrayList<GClause> sampledClauses = new ArrayList<GClause>();
	public ArrayList<Integer> sampledAtoms = new ArrayList<Integer>();
	public LinkedHashSet<Integer> sampledAtomsSet = new LinkedHashSet<Integer>();

	LinkedHashMap<Integer, GClause> singletons = null;

	public LinkedHashMap<BitSet, myInt> mleTallies = new LinkedHashMap<BitSet, myInt>();


	/**
	 * Data structures for fast cost calculation for MLE inference.
	 */
	public double[] bitmaps_weight;
	public BitSet[] bitmaps_signature;
	public BitSet[] bitmaps_mask;
	public Object[] clauseToFFCID;

	public BitSet empty = new BitSet();
	public boolean isCompiled = false;
	public String clausesSignature = null;

	public LinkedHashMap<GAtom, Integer> localAtomID = new LinkedHashMap<GAtom, Integer>();
	public LinkedHashMap<Integer, GAtom> globalAtom = new LinkedHashMap<Integer, GAtom>();
	public LinkedHashMap<Integer, BitSet> localAtomToUnitBitmap =
			new LinkedHashMap<Integer, BitSet>();

	public LinkedHashSet<Integer> isQueryForLearning = new LinkedHashSet<Integer>();
	public LinkedHashSet<Integer> isFiexedForLearning = new LinkedHashSet<Integer>();

	public LinkedHashMap<Integer, LinkedHashSet<Integer>> localAtom2Clause = 
			new LinkedHashMap<Integer, LinkedHashSet<Integer>>();
	public LinkedHashMap<Integer, LinkedHashSet<Integer>> localClause2Atom = 
			new LinkedHashMap<Integer, LinkedHashSet<Integer>>();

	public LinkedHashMap<Integer, LinkedHashSet<Integer>> keyToLocalAtoms = 
			new LinkedHashMap<Integer, LinkedHashSet<Integer>>();
	public LinkedHashMap<Integer, LinkedHashSet<Integer>> localAtomsToKey = 
			new LinkedHashMap<Integer, LinkedHashSet<Integer>>();

	public LinkedHashMap<String, Double> cweights = null;

	public void updateWeight(Map<String, Double> currentWeight){
		int ct = 0;
		for(GClause gc : this.clauses){

			//System.out.print("*");

			double newWeight = 0;
			for(String ffcid : gc.ffcid){
				if(ffcid.startsWith("-")){
					if(currentWeight.containsKey(ffcid.substring(1))){
						newWeight -= currentWeight.get(ffcid.substring(1));
					}
				}else{
					if(currentWeight.containsKey(ffcid)){
						newWeight += currentWeight.get(ffcid);
					}else{
						newWeight += Config.hard_weight;
					}
				}
			}
			gc.weight = newWeight;

			BitSet bitmap_signature = new BitSet();
			BitSet bitmap_mask = new BitSet();


			for(int lit : gc.lits){
				int atomid = Math.abs(lit);
				int localID = this.localAtomID.get(this.atoms.get(atomid));

				if(lit > 0){
					bitmap_signature.set(localID);
				}
				bitmap_mask.set(localID);
			}

			bitmaps_signature[ct] = bitmap_signature;
			bitmaps_mask[ct] = bitmap_mask;
			bitmaps_weight[ct] = gc.weight;
			clauseToFFCID[ct] = gc.ffcid;

			ct++;

		}

	}
	
	public static void computeStats(MRF mrf) {
		Stats.numberGroundAtoms = mrf.atoms.size();
		Stats.numberGroundClauses = mrf.clauses.size();
		int numberUnits = 0;
		for (GClause gc : mrf.clauses) {
			if (gc.isUnitClause() && gc.isHardClause()) {
				numberUnits++;
			}
		}
		Stats.numberUnits = numberUnits;
	}

	/**
	 * compile current MRF into a set of bitmaps, so that the calculation of
	 * costs can be conducted efficiently.
	 */
	public void compile(LinkedHashMap<String, Double>... weights){

		//System.out.println("compile 1");

		// only compile once
		if(isCompiled){
			return;
		}

		bitmaps_weight = new double[clauses.size()];
		bitmaps_signature = new BitSet[clauses.size()];
		bitmaps_mask = new BitSet[clauses.size()];
		clauseToFFCID = new Object[clauses.size()];

		//System.out.println("compile 2");

		// assign each gatom with an id starts from 0
		int ct = 1;		// TODO: bugs
		for(Integer atom: this.coreAtoms){


			localAtomID.put(this.atoms.get(atom), ct);
			globalAtom.put(ct, this.atoms.get(atom));

			if(!(this.atoms.get(atom).isquery_evid)){
				this.isQueryForLearning.add(ct);
			}else{
				if(this.atoms.get(atom).truth){
					this.isFiexedForLearning.add(ct);
				}
			}

			BitSet tmp = new BitSet();
			tmp.set(ct);

			localAtomToUnitBitmap.put(ct, tmp);

			ct++;
		}

		//System.out.println("compile 3");

		// for each ground clause, compile it into one bitmaps
		//		clause: c1 v !c2 v c3   (there are other atoms c4 and c5)
		//		=> 10100 as signature, 11100 as mask
		// given a world T
		// sat = #1 in [ (T ^ 10100) v !(T v 10100) ] ^ 11100
		ct = 0;
		for(GClause gc : this.clauses){

			//System.out.print("*");

			if(weights.length != 0){
				double newWeight = 0;
				for(String ffcid : gc.ffcid){
					if(ffcid.startsWith("-")){
						newWeight -= weights[0].get(ffcid.substring(1));
					}else{
						newWeight += weights[0].get(ffcid);
					}
				}
				gc.weight = newWeight;
			}

			BitSet bitmap_signature = new BitSet();
			BitSet bitmap_mask = new BitSet();

			boolean iskey = true;

			ArrayList<Integer> tosetSignature = new ArrayList<Integer>();
			ArrayList<Integer> tosetMask = new ArrayList<Integer>();
			for(int lit : gc.lits){

				if(lit > 0){
					iskey = false;
				}

				int atomid = Math.abs(lit);
				int localID = this.localAtomID.get(this.atoms.get(atomid));

				if(lit > 0){
					bitmap_signature.set(localID);
				}
				bitmap_mask.set(localID);

				if(!localAtom2Clause.containsKey(localID)){
					localAtom2Clause.put(localID, new LinkedHashSet<Integer>());
				}
				if(!localClause2Atom.containsKey(ct)){
					localClause2Atom.put(ct, new LinkedHashSet<Integer>());
				}

				localAtom2Clause.get(localID).add(ct);
				localClause2Atom.get(ct).add(localID);	
			}

			if(iskey == true && gc.weight >= Config.hard_weight){
				for(int lit : gc.lits){
					int atomid = Math.abs(lit);
					int localID = this.localAtomID.get(this.atoms.get(atomid));

					if(!keyToLocalAtoms.containsKey(gc.id)){
						keyToLocalAtoms.put(gc.id, new LinkedHashSet<Integer>());
					}

					keyToLocalAtoms.get(gc.id).add(localID);

					if(!localAtomsToKey.containsKey(localID)){
						localAtomsToKey.put(localID, new LinkedHashSet<Integer>());
					}

					localAtomsToKey.get(localID).add(gc.id);

				}

			}

			bitmaps_signature[ct] = bitmap_signature;
			bitmaps_mask[ct] = bitmap_mask;

			bitmaps_weight[ct] = gc.weight;
			clauseToFFCID[ct] = gc.ffcid;

			ct++;

		}

		//System.out.println("compile 4");
		/*
		for(ct=0;ct < bitmaps_mask.length; ct++){
			BitSet mask = bitmaps_mask[ct];
			if(mask.getPositions().size() == 1 && bitmaps_weight[ct] > 0.1){
				UIMan.println("One");
			}
		}
		 */
	}



	/*
	public int nClause;
	public void compile_sgd(){

		nClause = 0;
		for(GClause gc : this.clauses){

			gc.f


		}
	}
	 */

	/**
	 * Given a possible world, returns the cost. <br />
	 * !!! IMPORTANT !!!
	 * Need the function {@link MRF::compile} to be invoked before. Due to efficiency
	 * considerations, this function does not check {@link MRF::isCompiled}.
	 * @param world
	 * @return
	 */
	public double getCost(BitSet world){
		double rs = 0;
		for(int ct=0;ct<bitmaps_weight.length;ct++){

			double weight = bitmaps_weight[ct];

			BitSet signature = bitmaps_signature[ct];
			BitSet mask = bitmaps_mask[ct];

			// clause: c1 v !c2 v c3   (there are other atoms c4 and c5)
			//		=> 10100 as signature, 11100 as mask
			// given a world T
			// sat = \exists 1 in [ (T ^ 10100) v !(T v 10100) ] ^ 11100

			BitSet TandSIG = (BitSet) world.clone();
			TandSIG.and(signature);

			BitSet notTorSIG = (BitSet) world.clone();
			notTorSIG.or(signature);

			TandSIG.and(mask);
			BitSet tocheck = (BitSet) mask.clone();

			tocheck.andNot(notTorSIG);
			tocheck.or(TandSIG);

			if(tocheck.isEmpty()){
				if(weight > 0){
					rs += weight;
				}
			}else{
				if(weight < 0){
					rs += -weight;
				}
			}

		}
		return rs;
	}

	public double getFlipDelta(BitSet world, int atomID){

		double delta = 0;
		for(int clause : this.localAtom2Clause.get(atomID)){
			delta -= -this.getCostOfClause(world, clause);
		}

		world.flip(atomID);

		for(int clause : this.localAtom2Clause.get(atomID)){
			delta += -this.getCostOfClause(world, clause);
		}

		world.flip(atomID);

		return delta;
	}

	public double getCostOfClause(BitSet world, int clauseID){
		double rs = 0;
		int ct = clauseID;

		double weight = bitmaps_weight[ct];

		BitSet signature = bitmaps_signature[ct];
		BitSet mask = bitmaps_mask[ct];

		// clause: c1 v !c2 v c3   (there are other atoms c4 and c5)
		//		=> 10100 as signature, 11100 as mask
		// given a world T
		// sat = \exists 1 in [ (T ^ 10100) v !(T v 10100) ] ^ 11100

		BitSet TandSIG = (BitSet) world.clone();
		TandSIG.and(signature);

		BitSet notTorSIG = (BitSet) world.clone();
		notTorSIG.or(signature);

		TandSIG.and(mask);
		BitSet tocheck = (BitSet) mask.clone();

		tocheck.andNot(notTorSIG);
		tocheck.or(TandSIG);

		if(tocheck.isEmpty()){
			if(weight > 0){
				rs += weight;
			}
		}else{
			if(weight < 0){
				rs += -weight;
			}
		}

		return rs;
	}

	public Integer[] getClauseTallies(BitSet world){

		Integer[] rs = new Integer[clauses.size()];

		for(int ct=0;ct<bitmaps_weight.length;ct++){

			rs[ct] = 0;

			double weight = bitmaps_weight[ct];

			BitSet signature = bitmaps_signature[ct];
			BitSet mask = bitmaps_mask[ct];

			// clause: c1 v !c2 v c3   (there are other atoms c4 and c5)
			//		=> 10100 as signature, 11100 as mask
			// given a world T
			// sat = \exists 1 in [ (T ^ 10100) v !(T v 10100) ] ^ 11100

			BitSet TandSIG = (BitSet) world.clone();
			TandSIG.and(signature);

			BitSet notTorSIG = (BitSet) world.clone();
			notTorSIG.or(signature);

			TandSIG.and(mask);
			BitSet tocheck = (BitSet) mask.clone();

			tocheck.andNot(notTorSIG);
			tocheck.or(TandSIG);

			// TODO: change to faster implementation of tally
			if(tocheck.isEmpty()){
				// tocheck == 000000... <--- false
				if(weight > 0){
					rs[ct] ++;
				}

			}else{
				// tocheck != 000000... <--- true
				if(weight < 0){
					rs[ct] ++;
				}
			}
		}

		return rs;
	}

	public Integer[] getClauseSat(BitSet world){

		Integer[] rs = new Integer[clauses.size()];

		for(int ct=0;ct<bitmaps_weight.length;ct++){

			rs[ct] = 0;

			double weight = bitmaps_weight[ct];

			BitSet signature = bitmaps_signature[ct];
			BitSet mask = bitmaps_mask[ct];

			// clause: c1 v !c2 v c3   (there are other atoms c4 and c5)
			//		=> 10100 as signature, 11100 as mask
			// given a world T
			// sat = \exists 1 in [ (T ^ 10100) v !(T v 10100) ] ^ 11100

			BitSet TandSIG = (BitSet) world.clone();
			TandSIG.and(signature);

			BitSet notTorSIG = (BitSet) world.clone();
			notTorSIG.or(signature);

			TandSIG.and(mask);
			BitSet tocheck = (BitSet) mask.clone();

			tocheck.andNot(notTorSIG);
			tocheck.or(TandSIG);

			// TODO: change to faster implementation of tally
			if(tocheck.isEmpty()){
				// tocheck == 000000... <--- false
				if(weight < 0){
					rs[ct] ++;
				}

			}else{
				// tocheck != 000000... <--- true
				if(weight > 0){
					rs[ct] ++;
				}
			}
		}

		return rs;
	}

	public String getWeightArray(){
		String rs = "{";
		for(int i=0;i<bitmaps_weight.length;i++){
			if(i == 0){
				rs = rs + bitmaps_weight[i];
			}else{
				rs = rs + "," + bitmaps_weight[i];
			}
		}
		rs = rs + "}";
		return rs;
	}

	public String getClauseArray(){
		String rs = "{";
		rs = rs + clausesSignature;
		rs = rs + "}";
		return rs;
	}

	public String getQueryArray(){
		String rs = "{";

		for(Integer i : this.globalAtom.keySet()){
			GAtom global = this.globalAtom.get(i);
			if(global.isquery){
				if(rs.equals("{")){
					rs = rs + i;
				}else{
					rs = rs + ", " + i;
				}
			}
		}

		rs = rs + "}";
		return rs;
	}

	public class myDouble{

		public double value = Double.NEGATIVE_INFINITY;

		public double logAdd(double logX, double logY) {

			if (logY > logX) {
				double temp = logX;
				logX = logY;
				logY = temp;
			}

			if (logX == Double.NEGATIVE_INFINITY) {
				return logX;
			}

			double negDiff = logY - logX;
			if (negDiff < -200) {
				return logX;
			}

			return logX + java.lang.Math.log(1.0 + java.lang.Math.exp(negDiff)); 
		}

		public void tallylog(double plus){
			value = logAdd(value, plus);
		}

	}


	public double logAdd(double logX, double logY) {

		if (logY > logX) {
			double temp = logX;
			logX = logY;
			logY = temp;
		}

		if (logX == Double.NEGATIVE_INFINITY) {
			return logX;
		}

		double negDiff = logY - logX;
		if (negDiff < -200) {
			return logX;
		}

		return logX + java.lang.Math.log(1.0 + java.lang.Math.exp(negDiff)); 
	}



	/**
	 * Array of unsatisfied GClauses under current atoms' truth setting.
	 */
	protected HashArray<GClause> unsat = new HashArray<GClause>();

	/**
	 * Index from GAtom ID to GClause.
	 */
	public LinkedHashMap<Integer, ArrayList<GClause>> adj = new LinkedHashMap<Integer, ArrayList<GClause>>();

	int numCriticalNodesLocal = 0;

	int numClausesInCut = 0;

	double weightClausesInCut = 0;

	/**
	 * Atoms that have been flipped since last saving to low.
	 */
	protected LinkedHashSet<Integer> dirtyAtoms = new LinkedHashSet<Integer>();

	/**
	 * The total cost of this MRF under current atoms' truth setting.
	 */
	protected double totalCost = Double.MAX_VALUE;

	public double getCost(){
		return totalCost;
	}

	/**
	 * Lowest cost ever seen.
	 */
	public double lowCost = Double.MAX_VALUE;

	/**
	 * Number of GClauses that is selected, and therefore must be
	 * satisfied by next SampleSAT invocation of MCSAT.
	 */
	protected int totalAlive = 0;

	/**
	 * The flag indicating whether MCSAT is running WalkSAT 
	 * or SampleSAT.
	 */
	public boolean sampleSatMode = false;

	int partID = 0;
	public long inferOps = 0;

	/**
	 * The MLN object.
	 */
	private MarkovLogicNetwork mln;


	public MarkovLogicNetwork getMLN(){
		return mln;
	}

	/**
	 * Reset low-cost to infinity.
	 */
	public void invalidateLowCost(){
		lowCost = Double.MAX_VALUE;
	}

	/**
	 * For research experiments!
	 * Split the MRF into multiple pieces by agglomerative clustering.
	 * Each piece contains up to 2/np of the total atoms.
	 * 
	 * @param np number of pieces
	 * @return the pieces, each as an individual MRF
	 */
	@SuppressWarnings("unused")
	private ArrayList<MRF> split(int np){
		Random r = SeededRandom.getInstance();
		ArrayList<MRF> pieces = new ArrayList<MRF>();
		ArrayList<Integer> as = new ArrayList<Integer>(atoms.keySet());
		LinkedHashSet<Integer> danglings = new LinkedHashSet<Integer>(atoms.keySet());
		Collections.shuffle(as);
		int imb = 1;// + r.nextInt(3);
		int maxs = (int) (atoms.size() * (1.0/np + r.nextDouble()*(1.0/np)));
		LinkedHashMap<Integer, MRF> amap = new LinkedHashMap<Integer, MRF>();
		int cur = 0;
		// assign seed nodes
		for(int i=0; i<np; i++){
			MRF m = new MRF(mln, i, atoms);
			m.ownsAllAtoms = false;
			pieces.add(m);
			m.numCriticalNodesLocal = 0;
			if(i==0){
				for(int k=imb; k>0; k--){
					int aid = as.get(cur++);
					GAtom a = atoms.get(aid);
					while(!a.critical()){
						aid = as.get(cur++);
						a = atoms.get(aid);
					}
					if(a.critical()){
						m.numCriticalNodesLocal ++;
					}
					amap.put(aid, m);
					m.addAtom(aid);
					danglings.remove(aid);
				}
			}else{
				int aid = as.get(cur++);
				GAtom a = atoms.get(aid);
				while(!a.critical()){
					aid = as.get(cur++);
					a = atoms.get(aid);
				}
				if(a.critical()){
					m.numCriticalNodesLocal ++;
				}
				amap.put(aid, m);
				m.addAtom(aid);
				danglings.remove(aid);
			}
		}
		for(int aid : atoms.keySet()){
			GAtom a = atoms.get(aid);
			a.cut = false;
		}

		// assign remaining nodes
		while(!danglings.isEmpty()){
			Iterator<Integer> it = danglings.iterator();
			while(it.hasNext()){
				int aid = it.next();
				LinkedHashSet<Integer> ns = getAtomNeighbors(aid);
				ArrayList<Integer> opts = new ArrayList<Integer>();
				for(int a : ns){
					if(amap.get(a) != null){
						opts.add(a);
					}
				}
				MRF m = pieces.get(r.nextInt(np));
				if(!opts.isEmpty()){
					m = amap.get(opts.get(r.nextInt(opts.size())));
					if(m.getCoreAtoms().size() > maxs){
						int ot = r.nextInt(np);
						while(ot == pieces.indexOf(m)){
							ot = r.nextInt(np);
						}
						m = pieces.get(ot);
					}
				}else{
					continue;
				}
				GAtom a = atoms.get(aid);
				if(a.critical()){
					m.numCriticalNodesLocal ++;
				}
				amap.put(aid, m);
				m.addAtom(aid);
				it.remove();
			}
		}

		double gcSize = 0;
		double gcWeight = 0;
		for(GClause gc : clauses){
			int aid = Math.abs(gc.lits[r.nextInt(gc.lits.length)]);
			MRF m = amap.get(aid);
			m.clauses.add(gc);
			gcSize ++;
			gcWeight += Math.abs(gc.weight);
		}
		ArrayList<Double> numLCs = new ArrayList<Double>();
		double cutSize = 0;
		double cutWeight = 0;
		for(MRF m : pieces){
			m.buildIndices();
			numLCs.add((double)m.numCriticalNodesLocal);
			cutSize += m.numClausesInCut;
			cutWeight += m.weightClausesInCut;
			DebugMan.log(m.numCriticalNodesLocal + ", ");
		}
		double gaSize = 0;
		double gaCritical = 0;
		double atomsCriticalCut = 0;
		double atomsCut = 0;
		for(GAtom a : atoms.values()){
			gaSize ++;
			if(a.critical()){
				gaCritical++;
			}
			if(a.cut){
				atomsCut++;
				if(a.critical()){
					atomsCriticalCut++;
				}
			}
		}

		DebugMan.log("np, " + np + ", ");
		double imbb = Collections.max(numLCs) / this.numCriticalNodesLocal;
		DebugMan.log("maxpart, " + UIMan.comma(imbb) + ", ");
		double atomsCutPortion = atomsCut / gaSize;
		DebugMan.log("atomsCut, " + UIMan.comma(atomsCutPortion) + ", ");
		double atomsCriticalCutPortion = atomsCriticalCut / gaCritical;
		DebugMan.log("atomsCriticalCut, " + UIMan.comma(atomsCriticalCutPortion) + ", ");
		double clausesCutPortion = cutSize / gcSize;
		DebugMan.log("clausesCut, " + UIMan.comma(clausesCutPortion) + ", ");
		double clausesWtCutPortion = cutWeight / gcWeight;
		DebugMan.log("clausesWtCut, " + UIMan.comma(clausesWtCutPortion) + ", ");

		return pieces;
	}

	/**
	 * For research experiments!
	 * Get all neighboring atoms of one atom.
	 * @param aid id of the core atom
	 * @return id of neighbors
	 */
	private LinkedHashSet<Integer> getAtomNeighbors(int aid){
		LinkedHashSet<Integer> ns = new LinkedHashSet<Integer>();
		for(GClause gc : adj.get(aid)){
			for(int lit : gc.lits){
				ns.add(Math.abs(lit));
			}
		}
		for(GClause gc : adj.get(-aid)){
			for(int lit : gc.lits){
				ns.add(Math.abs(lit));
			}
		}
		return ns;
	}


	/**
	 * Discard all data structures, in hope of facilitating faster GC.
	 */
	public void discard() {
		atoms = null;
		getCoreAtoms().clear();
		clauses.clear();
		unsat.clear();
		adj.clear();
	}

	/**
	 * Add an atom into this MRF.
	 * @param aid id of the atom
	 */
	public void addAtom(int aid){
		atomsArrayList.add(aid);
		getCoreAtoms().add(aid);
	}

	/**
	 * Default constructor.
	 * Does not really do anything.
	 */
	public MRF(MarkovLogicNetwork mln){
		this.mln = mln;
	}

	/**
	 * 
	 * @param partID id of this MRF
	 * @param gatoms ground atoms
	 */
	public MRF(MarkovLogicNetwork mln, int partID, LinkedHashMap<Integer, GAtom> gatoms){
		this.partID = partID;
		atoms = gatoms;
	}

	/**
	 * Test if a given atom is "owned" by this MRF.
	 * An atom may not belong to this MRF if this MRF represents
	 * a partition of a component that has multiple partitions.
	 * 
	 * @param aid id of the atom
	 * 
	 */
	protected boolean ownsAtom(int aid){
		return ownsAllAtoms || getCoreAtoms().contains(aid);
	}

	protected boolean ownsAtom(int aid, LinkedHashSet<Integer> notChange){

		if(notChange != null && notChange.contains(aid)){
			return false;
		}

		return ownsAllAtoms || getCoreAtoms().contains(aid);

	}

	/**
	 * If current truths have the lowest cost, save them.
	 * @param cost the current cost
	 */
	protected void saveLowTruth(){
		if(dirtyAtoms.size() > 0){ // incremental dump
			for(int aid : dirtyAtoms){
				GAtom ga = atoms.get(aid);
				ga.lowTruth = ga.truth;
			}
			dirtyAtoms.clear();
		}else{ // force flushing
			for(GAtom n : atoms.values()){
				n.lowTruth = n.truth;
			}
		}
	}


	private void saveTruthAsLow(){
		for(GAtom n : atoms.values()){
			n.lowTruth = n.truth;
		}
	}


	/**
	 * Check if a given literal is true under current truth assignment.
	 * @param lit the literal represented as an integer
	 * 
	 */
	protected boolean isTrueLit(int lit){
		return (lit > 0 && atoms.get(lit).truth) 
				|| (lit < 0 && !atoms.get(-lit).truth);
	}

	/**
	 * Test if a clause is always true no matter how we flip flippable atoms.
	 * @param gc the clause
	 * 
	 */
	protected boolean isAlwaysTrue(GClause gc){
		int fixed = 0;
		for(int lit : gc.lits){
			if(!ownsAtom(Math.abs(lit))){
				if(isTrueLit(lit)){
					return true;
				}
				fixed ++;
			}
		}
		return fixed == gc.lits.length;
	}

	/**
	 * Fix the truth value of an atom.
	 * @param aid id of the atom
	 * @param t truth value to be fixed
	 */
	
	private ArrayList<Integer> fixedAtomList = new ArrayList<Integer>();
	protected void fixAtom(int aid, boolean t){
		if (aid == 10 || aid == 78 || aid == 17 || aid == 124 || aid == 122) {
			UIMan.verbose(3, "here");
		}
		fixedAtomList.add(t ? aid : -aid);
		UIMan.verbose(3, fixedAtomList.toString());
		GAtom a = atoms.get(aid);
		a.truth = t;
		a.fixed = true;
	}



	/**
	 * Retain a subset of currently satisfied clauses, according
	 * to the sampling method of MC-SAT.
	 * 
	 * @return the number of retained clauses
	 */
	protected int retainSomeGoodClauses(){
		int numGood = 0;
		totalAlive = 0;
		for(GClause c : clauses){
			if(c.cost() == 0) numGood++;
			if(c.selectMCSAT()){
				c.dead = false;
				++ totalAlive;
			}else{
				c.dead = true;
			}
		}
		if(!Config.learning_mode)
			UIMan.verbose(2, "    Retained #clauses = " + UIMan.comma(totalAlive) + 
					" out of " + UIMan.comma(numGood) + " non-violated clauses (" + 
					UIMan.comma(clauses.size()) + " in total)");
		return totalAlive;
	}


	/**
	 * Unfix all atoms.
	 */
	protected void unfixAllAtoms(){
		for(GAtom a : atoms.values()){
			a.fixed = false;
		}
	}


	/**
	 * Assign the recorded low-cost truth values to current truth values.
	 */
	public void restoreLowTruth(){
		for(GAtom n : atoms.values()){
			n.truth = n.lowTruth;
		}
	}

	/**
	 * Reset all clauses to be alive.
	 */
	public void enableAllClauses(){
		totalAlive = clauses.size();
		for(GClause c : clauses){
			c.dead = false;
		}
	}

	/**
	 * Build literal-->clauses index.
	 * Used by WalkSAT.
	 */
	public void buildIndices(){
		if(!adj.isEmpty()) return;
		adj.clear(); //TODO(ericgribkoff) if it's empty, clearing does nothing
		for(GClause f : clauses){
//			UIMan.verbose(3, "building index for " + f);
			for(int lit : f.lits){
				ArrayList<GClause> plist = adj.get(lit);
				if(plist == null){
					plist = new ArrayList<GClause>();
					adj.put(lit, plist);
				}
				plist.add(f);
			}
		}
	}
	
	public void buildIndicesSampledClauses(){
		adj.clear();
		for(GClause f : sampledClauses){
//			UIMan.verbose(3, "building index for " + f);
			for(int lit : f.lits){
				ArrayList<GClause> plist = adj.get(lit);
				if(plist == null){
					plist = new ArrayList<GClause>();
					adj.put(lit, plist);
				}
				plist.add(f);
			}
		}
	}

	private Random rand = SeededRandom.getInstance();

	/**
	 * Coin flipping.
	 * @param p probability of returning true
	 * 
	 */
	protected boolean testChance(double p){
		return rand.nextDouble() < p;
	}

	@SuppressWarnings("unused")
	private void testKeyConstraints(){

		LinkedHashMap<Integer, myInt> counter = new LinkedHashMap<Integer, myInt>();

		for(GAtom g : atoms.values()){

			Integer key = keyBlock.gatom2key.get(g);

			if(key == null){
				continue;
			}

			if(!counter.containsKey(key)){
				counter.put(key, new myInt(0));
			}
			if(g.truth == true){
				counter.get(key).addOne();
			}
		}

		for(Integer key : counter.keySet()){
			if(counter.get(key).value >= 2){
				System.err.print("DUPLICATION!!!");
			}
		}

	}

	public boolean willChange(int lit, int[] lits){
		for(int a : lits){
			if(a == -lit){
				return false;
			}
		}
		return true;
	}


	/**
	 * Initialize the state of the MRF.
	 */
	public void initMRF() {
		switch(initStrategy){
		case ALL_FALSE:
			assignAllFalseTruthValues();
			break;
		case COIN_FLIP:
			assignRandomTruthValues();
			break;
		case GREEDY:
			assignGreedyTruthValues();
			break;
		case COPY_LOW:
			restoreLowTruth();
			break;
		case NO_CHANGE:
			break;
		}
//		if(usingBlocks)	maintainKeyConstraints();
	}

//	private void maintainKeyConstraints(){
//		for(GAtom n : atoms.values()){
//			if(!keyBlock.hasKey(n)){
//				continue;
//			}
//
//			if(n.truth == true){
//				ArrayList<GAtom> mates = keyBlock.getBlockMates(n);
//				for(GAtom shouldBeFalse : mates){
//					shouldBeFalse.truth = false;
//				}
//			}
//		}
//	}

	/**
	 * Set all atoms to false.
	 */
	private void assignAllFalseTruthValues(){
		if(ownsAllAtoms){
			for(GAtom n : atoms.values()){
				n.truth = false;
			}
		}else{
			for(int aid : getCoreAtoms()){
				atoms.get(aid).truth = false;
			}
		}
	}

	/**
	 * Set random atom truth values.
	 */
	private void assignRandomTruthValues(){
		Random rand = SeededRandom.getInstance();
		for(GAtom n : atoms.values()){
//			if(n.fixed) continue; //TODO(ericgribkoff) Find out when "fixed" is set
			n.truth = rand.nextBoolean();
		}
	}

	/**
	 * Assign inital truth values according to some
	 * ad hoc and heuristic stats.
	 */
	private void assignGreedyTruthValues(){
		LinkedHashMap<Integer, Double> wts = new LinkedHashMap<Integer, Double>();
		if(ownsAllAtoms){
			for(GAtom n : atoms.values()){
				wts.put(n.id, 0.0);
			}
		}else{
			for(int aid : getCoreAtoms()){
				wts.put(aid, 0.0);
			}
		}
		for(GClause f : clauses){
			if(!ownsAllAtoms && isAlwaysTrue(f)) continue;
			for(int lit : f.lits){
				int a = Math.abs(lit);
				Double prev = wts.get(a);
				if(prev != null){
					wts.put(a, prev + Math.signum(lit) * f.weight);
				}
			}
		}
		for(int aid : wts.keySet()){
			GAtom n = atoms.get(aid);
			if(wts.get(n.id) > 0){
				n.truth = true;
			}else{
				n.truth = false;
			}
		}
	}

	/**
	 * Calculate the number of true literals in a clause.
	 * @param f
	 */
	private void calcNSAT(GClause f){
		int nsat = 0;
		for(int lit : f.lits){
			if(isTrueLit(lit)){
				nsat++;
			}
		}
		f.nsat = nsat;
	}

	private ArrayList<GAtom> getFlipSequence(GAtom a){
		ArrayList<GAtom> ret = new ArrayList<GAtom>();

		if(a.truth == true || keyBlock.hasKey(a) == false){
			ret.add(a);
			return ret;
		}else{

			ArrayList<GAtom> mates = keyBlock.getBlockMates(a);
			for(GAtom g : mates){
				if(g == a || g.truth == true){
					ret.add(g);
				}
			}
			return ret;
		}

	}


	/**
	 * Compute total cost and per-atom delta cost.
	 * The delta cost of an atom is the change in the total cost if
	 * this atom is flipped.
	 * @return total cost
	 */
	protected double calcCostsFast(){
		totalCost = 0;
		unsat.clear();
		// reset stats
		if(ownsAllAtoms){
			for(GAtom n : atoms.values()){
				n.resetDelta();
			}
		}else{
			for(int aid : getCoreAtoms()){
				atoms.get(aid).resetDelta();
			}
		}

		// recompute stats
		for(GClause f : clauses){
			calcNSAT(f);
			if(f.dead) continue;
			totalCost += f.cost();
			if(!ownsAllAtoms && isAlwaysTrue(f)){
				continue;
			}
			if(f.cost() > 0){
				unsat.add(f);
			}
		}

		return totalCost;
	}


	/**
	 * Compute total cost and per-atom delta cost.
	 * The delta cost of an atom is the change in the total cost if
	 * this atom is flipped.
	 * @return total cost
	 */
	protected double calcCosts(){
		totalCost = 0;
		unsat.clear();
		// reset stats

		for (int atomid : sampledAtoms) {
			GAtom n = atoms.get(atomid);
			n.resetDelta();
		}
		
		// recompute stats
		for(GClause f : sampledClauses){
			calcNSAT(f);
			totalCost += f.cost();
			if(f.cost() > 0){
				unsat.add(f);
			}
		}

//		for (int atomid : sampledAtoms) {
//			GAtom n = atoms.get(atomid);
//			
//			boolean flipToVal = !n.truth;
//			int trueLiteralID, falseLiteralID;
//			
//			if (flipToVal) {
//			    trueLiteralID = n.id;
//			    falseLiteralID = -n.id;
//			} else {
//			    trueLiteralID = -n.id;
//			    falseLiteralID = n.id;
//			}
//			
//			// NSat records the number of literals satisfying a clause:
//			// e.g., if the clause is A v !B v !C, and A=true, B=true, C=false,
//			// the clause's NSat value is 3
//			ArrayList<GClause> addNSAT = adj.get(trueLiteralID);
//			ArrayList<GClause> minusNSAT = adj.get(falseLiteralID);
//			
//			LinkedHashMap<GClause, Integer> delta = new LinkedHashMap<GClause, Integer>();
//			for(GClause gc : minusNSAT){
//				if(!delta.containsKey(gc)){
//					delta.put(gc, 0);
//				}
//				delta.put(gc, delta.get(gc)-1);
//			}
//			for(GClause gc : addNSAT){
//				if(!delta.containsKey(gc)){
//					delta.put(gc, 0);
//				}
//				delta.put(gc, delta.get(gc)+1);
//			}
//
//			for(GClause gc : delta.keySet()){
//				Integer del = delta.get(gc);
//				if(gc.nsat > 0 && gc.nsat + del <= 0){
//					n.assignUnsatPotential(gc);
//				}else if (gc.nsat == 0 && gc.nsat + del > 0){
//					n.assignSatPotential(gc);
//				}
//			}
//			
//		}

		return totalCost;
	}


	/**
	 * Track ground clause violations to fo-clauses.
	 * Stats are records on a per fo-clause basis.
	 * 
	 * @see	tuffy.helper.Stats#reportMostViolatedClauses
	 */
	public void auditClauseViolations(){
		restoreLowTruth();
		totalCost = 0;
		for(Clause c : mln.getAllNormalizedClauses()){
			c.cost = 0;
			c.violations = 0;
			c.violatedGClauses = new ArrayList<GClause>();
		}
		for(GClause f : clauses){
			calcNSAT(f);
			double c = f.cost();
			int kv = 0;
			if(c > 0){
				totalCost += c;
				for(int fc : f.fcid){
					Clause cl = mln.getClauseById(fc);
					boolean pen0 = cl.isPositiveClause();
					if(fc < 0) pen0 = !pen0;
					if(pen0 == (f.nsat == 0)){
						kv ++;
					}
				}
				if(kv > 0)	{
					for(int fc : f.fcid){
						Clause cl = mln.getClauseById(fc);
						boolean pen0 = cl.isPositiveClause();
						if(fc < 0) pen0 = !pen0;
						if(pen0 == (f.nsat == 0)){
							cl.cost += (c/kv);
							cl.violations += 1.0/kv;
							cl.violatedGClauses.add(f);
						}
					}
				}else{
					// System.err.println("found unaccounted-for ground clauses");
				}
			}
		}
	}

	/**
	 * Recalculate total cost.
	 * @return updated total cost
	 */
	public double recalcCost(){
		totalCost = 0;
		for(GClause f : clauses){
			calcNSAT(f);
			totalCost += f.cost();
		}
		return totalCost;
	}

	public double recalcCostNotOverrideOldCost(){
		double atotalCost = 0;
		for(GClause f : clauses){
			calcNSAT(f);
			atotalCost += f.cost();
		}
		return atotalCost;
	}

	public LinkedHashSet<Integer> getCoreAtoms() {
		return coreAtoms;
	}




	/****************************************
	 * MCSAT Below
	 ***************************************/


	/**
	 * This map records the expectation of #violation for
	 * each clause. This is filled by {@link MCSAT#calcExpViolation()}.
	 */
	public LinkedHashMap<String, Double> expectationOfViolation = null;

	/**
	 * This map records the expectation of square #violation for
	 * each clause. This is filled by {@link MCSAT#calcExpViolation()}.
	 */
	public LinkedHashMap<String, Double> expectationOfSquareViolation = null;

	/**
	 * This map records the tallies for calculating E(v_i*v_j).
	 */
	public LinkedHashMap<String, Long> clauseNiNjViolationTallies = null;

	/**
	 * This map records the expectation of E(v_i*v_j). 
	 * This is filled by {@link MCSAT#calcExpViolation()}.
	 */
	public LinkedHashMap<String, Double> expectationOfNiNjViolation = null;

	/**
	 * This array records the expection of #satisfaction for
	 * each clause. This is filled by {@link MCSAT#calcExpViolation()}.
	 */
	public LinkedHashMap<String, Double> expectationOfSatisfication = null;


	/**
	 * This array records total number of violation for a clause.
	 * Dividing this number by {@link MCSAT#nClauseVioTallies}
	 * will give the estimated expectation of #violation. 
	 */
	public LinkedHashMap<String, Long> clauseVioTallies = null;

	/**
	 * This array records total number of square violation for a clause.
	 * Dividing this number by {@link MCSAT#nClauseVioTallies}
	 * will give the estimated expectation of #violation. 
	 */
	public LinkedHashMap<String, Long> clauseSquareVioTallies = null;

	/**
	 * This array records total number of satisfaction for a clause.
	 */
	public LinkedHashMap<String, Long> clauseSatTallies = null;

	/**
	 * Number of iterations of tallies. 
	 */
	private int nClauseVioTallies = 0;

	/**
	 * Kill soft clauses.
	 * 
	 * @return the number of hard clauses
	 */
	public int retainOnlyHardClauses(){
		int numHard = 0;
		for(GClause c : clauses){
			// Eric: Rewriting for clarity, under assumption a hard clause should never be dead
			// after this function returns
			if (c.isHardClause()) {
				c.dead = false;
				numHard++;
			} else {
				c.dead = true;
			}
		}
		totalAlive = numHard;
		return numHard;
	}
	
	
	/**
	 * SampleSAT (with WalkSAT inside), used to uniformly sample a zero-cost world.
	 * WalkSAT is used as a SAT solver to find the first (quasi-)zero-cost world.
	 * Simulated annealing (SA) is stochastically performed to wander around.
	 * @param nSteps
	 * 
	 * @return true iff a zero-cost world was reached
	 */
	// TODO(ericgribkoff) Verify this implementation, with extra concept(s) of owned atoms,
	// learning mode, etc. discarded
	public boolean walkSAT(long nSteps) {
		return false;
	}
	
	//Returns # of clauses newly satisfied - # of clauses newly unsatisfied
	public int deltaOfFlip(int atomid) {
		GAtom n = atoms.get(atomid);
		boolean flipToVal = !n.truth;
		int trueLiteralID, falseLiteralID;
		if (flipToVal) {
		    trueLiteralID = n.id;
		    falseLiteralID = -n.id;
		} else {
		    trueLiteralID = -n.id;
		    falseLiteralID = n.id;
		}
		
		int delta = 0;
		if (adj.containsKey(trueLiteralID)) {
			for (GClause gc : adj.get(trueLiteralID)) {
				if (gc.nsat == 0) {
					delta++;
//					UIMan.verbose(3, "Delta + 1: " + gc);
				}
			}
		}
		if (adj.containsKey(falseLiteralID)) {
			for (GClause gc : adj.get(falseLiteralID)) {
				if (gc.nsat == 1) {
					delta--;
//					UIMan.verbose(3, "Delta - 1: " + gc);
				}
			}
		}
		return delta;
	}
	
	public void printAllClauses() {
		UIMan.verbose(3, "All Clauses:");
		for (GClause c : clauses) {
			String truthVals = "";
			for (int a : c.lits) {
				if (a > 0) {
					truthVals += " " + atoms.get(Math.abs(a)).truth;
				} else {
					truthVals += " " + !atoms.get(Math.abs(a)).truth;
				}
			}
			UIMan.verbose(3, c.toString() + truthVals);
		}
	}
	
	public void printSampledClauses() {
		UIMan.verbose(3, "Sampled Clauses:");
		for (GClause c : sampledClauses) {
			String truthVals = "";
			for (int a : c.lits) {
				if (a > 0) {
					truthVals += " " + atoms.get(Math.abs(a)).truth;
				} else {
					truthVals += " " + !atoms.get(Math.abs(a)).truth;
				}
			}
			UIMan.verbose(3, c.toString() + truthVals);
		}
	}
	
	public void printSampledAtoms() {
		UIMan.verbose(3, "Atom Truth Settings:");
		for (int atomid : sampledAtoms) {
			GAtom a = atoms.get(atomid);
			UIMan.verbose(3, atomid + " : " + a.truth);
		}
	}
	
    public boolean sampleSAT(long nSteps) {
    	return sampleSAT(nSteps, false);
    }
	public boolean sampleSAT(long nSteps, boolean walkSATMode){
		UIMan.verbose(3, "    Running SampleSAT for " + UIMan.comma(nSteps) + " flips...");

//		printSampledClauses();
		
		if (sampledClauses.size() == 0) {
			return true;
		}
		
//		if(adj.isEmpty()) buildIndices();
		buildIndicesSampledClauses();
		
		Random rand = SeededRandom.getInstance();

		initMRF(); // TODO(ericgribkoff) Verify initialization code
		
//		printSampledAtoms();
		
		lowCost = calcCosts();
		dirtyAtoms.clear();
		
//		saveTruthAsLow(); //TODO(ericgribkoff) Need to save low truth as a satisfying state for the current
		// set of hard clauses; this doesn't seem to be doing that, need to call before initializing atoms
		// to random values
		
		boolean foundNewTruth = false;
		
		long flip;
		for(flip = 1; flip <= nSteps; flip++){
			// check if we have reached terminal condition
			if(unsat.isEmpty()){
				saveLowTruth();
				foundNewTruth = true;
				if (walkSATMode) {
					return true; // Stop if we are only interested in finding a solution, rather than
					 			 // sampling solutions
				}
			}

			// id of atom to be flipped
			int picked = 0;

			/**
			 * pick an atom to flip in one of two ways:
			 * WalkSAT or SA
			 */
			//TODO(ericgribkoff) Why is 0.5 hardcoded??? And what is 0.0001?
//			if(!walkSATMode && (totalCost <= 0.0001 
//					|| rand.nextDouble() <= 0.5) || unsat.isEmpty()){ 
			if(!walkSATMode && (rand.nextDouble() <= Config.simulatedAnnealingSampleSATProb || unsat.isEmpty())){ 
				// SA step: randomly pick an atom
				picked = sampledAtoms.get(rand.nextInt(sampledAtoms.size()));
				double randVal = rand.nextDouble();
				int delta = deltaOfFlip(picked);
				double threshold = Math.exp(delta*Config.samplesat_sa_coef);
				if(randVal > threshold){
					continue;
				}
			}else{
				// WalkSAT step
				GClause lucky = unsat.getRandomElement();
				if(lucky.isPositiveClause()){
					// a positive clause
					if(testChance(Config.walksat_random_step_probability)){
						// random flip
						picked = Math.abs(lucky.lits[rand.nextInt(lucky.lits.length)]);
					}else{
						// greedy flip
						double maxDelta = -Double.MAX_VALUE; //TODO(ericgribkoff) Looking for MAX!
						int nrivals = 0;
						for(int lit : lucky.lits){
							int a = Math.abs(lit);
							int delta = deltaOfFlip(a);
							if(delta > maxDelta){
								picked = a;
								maxDelta = delta;
								nrivals = 1;
							}else if(delta == maxDelta){
								if(testChance(1.0/(++nrivals))){
									picked = a;
								}
							}
						}
					}
				}else{
					//TODO(ericgribkoff) Why handle negative weight clauses in SampleSAT??? Why not just
					//pass in their negations? Is this even correct if we are treating the negation
					// of a clause as a single clause (instead of each term becoming its own clause)
					// For now, leaving this alone - we are not using negative weights, so support for
					// this is not a priority
					
					// a negative clause
					ArrayList<Integer> cands = new ArrayList<Integer>();
					for(int lit : lucky.lits){
						// The clause has negative weight; so we want to flip a literal currently satisfying
						// the clause
						if(isTrueLit(lit) && ownsAtom(Math.abs(lit))){
							cands.add(Math.abs(lit));
						}
					}
					if(cands.size() == 1){
						picked = cands.get(0);
					}else{
						if(testChance(Config.walksat_random_step_probability)){
							picked = cands.get(rand.nextInt(cands.size()));
						}else{
							double maxDelta = -Double.MAX_VALUE;
							int nrivals = 0;
							for(int a : cands){
								// TODO: FIXED
								int delta = deltaOfFlip(a);
								if(delta > maxDelta){
									picked = a;
									maxDelta = delta;
									nrivals = 1;
								}else if(delta == maxDelta){
									if(testChance(1.0/(++nrivals))){
										picked = a;
									}
								}
							}
						}
					}
				}
			}
			
			// Flip the picked atom
			if (flipAtom(picked)) {
				foundNewTruth = true;
			}
		}
		return foundNewTruth;
	}
	
	// Returns true if flip leads to new low truth (could actually lead back to same low truth
	// as original, but that's ok)
	private boolean flipAtom(int picked) {
		if(picked == 0) return false;
		
		GAtom atom = atoms.get(picked);
		if(atom.fixed) return false; // TODO(ericgribkoff) Check when atoms become fixed
		
		/**
		 *  flip the picked atom
		 */
//		totalCost += atom.delta();
		atom.truth = !atom.truth;
		dirtyAtoms.add(picked);
		atom.invertDelta();
		
		// update stats
		ArrayList<GClause> tlfac, flfac;
		if(atom.truth){
			tlfac = adj.get(atom.id);
			flfac = adj.get(-atom.id);
		}else{
			flfac = adj.get(atom.id);
			tlfac = adj.get(-atom.id);
		}

		// TFLAC = Clauses which become true by the flip just made
		if(tlfac != null){
			for(GClause f : tlfac){
				///////////////////////////////////////////////
				// Ce flip the following two lines on Nov. 29
				///////////////////////////////////////////////
				++ f.nsat;
				if(f.dead) continue;

				int nsat = f.nsat;
				if(nsat==1){
					if(f.weight >= 0) {
						unsat.removeObj(f);
					}else{
						unsat.add(f);
					}
					for(int lit : f.lits){
						int a = Math.abs(lit);
						if(a==picked) continue;
						atoms.get(a).revokeSatPotential(f);
					}
				}else if(nsat==2){
					for(int lit : f.lits){
						int a = Math.abs(lit);
						if(a==picked) continue;
						GAtom n = atoms.get(a);
						if((lit>0) == n.truth && willChange(lit, f.lits)){
							n.revokeUnsatPotential(f);
							break;
						}
					}
				}
			}
		}
		if(flfac != null){
			for(GClause f : flfac){
				///////////////////////////////////////////////
				// Ce flip the following two lines on Nov. 29
				///////////////////////////////////////////////
				-- f.nsat;
				if(f.dead) continue;

				int nsat = f.nsat;
				if(nsat==0){
					if(f.weight <= 0) {
						unsat.removeObj(f);
					}else{
						unsat.add(f);
					}
					for(int lit : f.lits){
						int a = Math.abs(lit);
						if(a==picked || !ownsAtom(a)) continue;
						atoms.get(a).assignSatPotential(f);
					}
				}else if(nsat==1){
					for(int lit : f.lits){
						int a = Math.abs(lit);
						if(a==picked || !ownsAtom(a)) continue;
						GAtom n = atoms.get(a);
						if((lit>0) == n.truth && willChange(lit, f.lits)){
							n.assignUnsatPotential(f);
							break;
						}
					}
				}
			}
		}

		if(unsat.isEmpty() && totalCost <= lowCost){
			saveLowTruth();
			return true;
		} else {
			return false;
		}
	}

	public void updateAtomMarginalProbs(int numSamples){
		if(ownsAllAtoms){
			for(GAtom n : atoms.values()){
				n.prob = ((float)(n.tallyTrue)) / numSamples;
				//TODO: CHECK n.truth = (n.prob >= 0.5);
				//	n.truth = (n.prob >= 0.5);
				n.truth = true;
			}
		}else{
			for(int aid : getCoreAtoms()){
				GAtom n = atoms.get(aid);
				n.prob = ((float)(n.tallyTrue)) / numSamples;
				//	n.truth = (n.prob >= 0.5);
				n.truth = true;
			}
		}
	}

	/**
	 * For each atom, increment its truth tally by one if it's currently true.
	 */
	private void updateAtomTruthTallies(){
		if(ownsAllAtoms){
			for(GAtom n : atoms.values()){
				if(n.truth) n.tallyTrue++;
			}
		}else{
			for(int aid : getCoreAtoms()){
				GAtom n = atoms.get(aid);
				if(n.truth) n.tallyTrue++;
			}
		}
	}

	private void resetAtomTruthTallies(){
		if(ownsAllAtoms){
			for(GAtom n : atoms.values()){
				n.tallyTrue = 0;
			}
		}else{
			for(int aid : getCoreAtoms()){
				GAtom n = atoms.get(aid);
				n.tallyTrue = 0;
			}
		}
	}

	public boolean isPowerOfTwo(int n){
		return ((n!=0) && (n&(n-1))==0);
	}

	
	public void clearSampledClauses() {
		sampledClauses = new ArrayList<GClause>();
	}
	
	public void includeClauseInSample(GClause c) {
		sampledClauses.add(c);
		// Add atoms in this clause to sampledAtoms
		for (int l: c.lits) {
			int atomid = Math.abs(l);
			if (!sampledAtomsSet.contains(atomid)) {
				sampledAtomsSet.add(atomid);
				sampledAtoms.add(atomid);
			}
		}
	}
	
	public void retainHardClauses(){
		for(GClause c : clauses){
			if (c.isHardClause()) {
				includeClauseInSample(c);
			}
		}
	}
	
	protected void retainSatisfiedClauses(){
		for(GClause c : clauses){
			if(c.isHardClause() || c.selectMCSAT()){
				includeClauseInSample(c);
			}
		}
	}
	
	/**
	 * Execute the MC-SAT algorithm.
	 * @param numSamples number of MC-SAT samples
	 * @param numFlips number of SampleSAT steps in each iteration
	 */
	@SuppressWarnings("unused")
	public double mcsat(int numSamples, long numFlips, DataMover... dmovers){

		//TODO(ericgribkoff) mcsat_cumulative seems to be for calling mcsat iteratively:
		// that is, run for 100 samples, get estimate of marginal, run for 100 more, improve
		// estimate, etc
		resetAtomTruthTallies();
		
		initStrategy = INIT_STRATEGY.COIN_FLIP;
		
		UIMan.println(">>> Running MC-SAT for " + numSamples + " samples...");
		
		// init
		UIMan.verbose(1, ">>> MC-SAT INIT: running WalkSAT on hard clauses...");
		
		clearSampledClauses();
		retainHardClauses();
		
		UIMan.verbose(1, "### hard clauses = " + sampledClauses.size());
		
		boolean foundSatisfyingAssignment = false;
		foundSatisfyingAssignment = sampleSAT(numFlips, true);
		
		UIMan.println("### Found initial satisfying assignment: " + foundSatisfyingAssignment);
		if (!foundSatisfyingAssignment) {
			ExceptionMan.die("WalkSAT failed to satisfy hard clauses");
		}

		sampleSatMode = true;
		// clear tallies of clauses.
		this.nClauseVioTallies = 0;
		this.clauseVioTallies = null;
		this.clauseSatTallies = null;
		this.expectationOfViolation = null;
		this.expectationOfSatisfication = null;
		this.clauseSquareVioTallies = null;
		this.expectationOfSquareViolation = null;
		this.clauseNiNjViolationTallies = null;
		this.expectationOfNiNjViolation = null;

		double sumCost = 0;

		// sample
		for(int i=1; i<=numSamples; i++){
			if (Timer.hasTimedOut()) {
				Config.mcsatTimedOut = true;
				Stats.numberSamplesAtTimeout = i-1;
				if (i > numSamples * Config.minPercentMcSatSamples) {
					numSamples = i;
					UIMan.print(">>>> Tuffy timed out after " + i + " samples, stopping MC-SAT");
					break;
				} else {
					ExceptionMan.die(">>>> Tuffy timed out after only " + i + " samples");
				}
			}

			UIMan.verbose(3, ">>> MC-SAT Sample #" + i + "");
			
			sumCost += performMCSatStep(numFlips);
			int curTime = (int) Timer.elapsedSeconds();

		}


		updateAtomMarginalProbs(numSamples);

		return sumCost;
	}

	public void updateAtomTruthFromMLE(ArrayList<BitSetIntPair> samples){

		for(BitSetIntPair top : samples){

			BitSet truthset = top.bitset;
			int freq = top.integer;

			if(this.ownsAllAtoms){
				for(Integer atom : this.atoms.keySet()){
					if(truthset.get(atom)){
						this.atoms.get(atom).truth = true;
						if(Config.mleTopK != -1){
							this.atoms.get(atom).top_truth_cache.add(true);
						}
					}else{
						this.atoms.get(atom).truth = false;
						if(Config.mleTopK != -1){
							this.atoms.get(atom).top_truth_cache.add(false);
						}
					}
				}
			}else{
				for(Integer atom : this.coreAtoms){
					if(truthset.get(atom)){
						this.atoms.get(atom).truth = true;
						if(Config.mleTopK != -1){
							this.atoms.get(atom).top_truth_cache.add(true);
						}
					}else{
						this.atoms.get(atom).truth = false;
						if(Config.mleTopK != -1){
							this.atoms.get(atom).top_truth_cache.add(false);
						}
					}
				}
			}

			if(Config.mleTopK == -1){
				break;
			}
		}

	}


	/**
	 * Calculating the different expectations by filling the LinkedHashMaps related to
	 * expectations in this class.
	 */
	public void calcExpViolation(){
		// has run updateClauseVoiTallies() before
		assert(this.clauseVioTallies != null);
		assert(this.nClauseVioTallies > 0);

		this.expectationOfViolation = new LinkedHashMap<String, Double>();
		this.expectationOfSatisfication = new LinkedHashMap<String, Double>();
		this.expectationOfSquareViolation = new LinkedHashMap<String, Double>();
		this.expectationOfNiNjViolation = new LinkedHashMap<String, Double>();

		for(String k : this.clauseVioTallies.keySet()){
			this.expectationOfViolation.put(k, 
					((double)this.clauseVioTallies.get(k)) / this.nClauseVioTallies);
			this.expectationOfSatisfication.put(k, 
					((double)this.clauseSatTallies.get(k)) / this.nClauseVioTallies);
			this.expectationOfSquareViolation.put(k, 
					((double)this.clauseSquareVioTallies.get(k)) / this.nClauseVioTallies );
		}

		for(String k : this.clauseNiNjViolationTallies.keySet()){
			this.expectationOfNiNjViolation.put(k, 
					((double)this.clauseNiNjViolationTallies.get(k)) 
					/ this.nClauseVioTallies);
		}
	}


	/**
	 * Change the weight of GClause based on updated weight
	 * of Clause. This new weight will be aware by MCSAT. The
	 * cost of flipping atom and the unsat set for GClause will
	 * be calculated automatically by this function.
	 * 
	 * @param currentWeight The weight of clauses to be flushed
	 * in this MCSAT instance.
	 */
	public void updateClauseWeights(LinkedHashMap<String, Double> currentWeight){
		for(GClause c : this.clauses){
			
			String[] ffcid = c.ffcid;
			c.weight = 0;
			for(int i=0;i<ffcid.length;i++){
				String newCID = ffcid[i];
				
				if(newCID.contains("fixed")){
					//c.weight = Config.hard_weight;
					break;
				}
				
				int signal = 1;
				if(newCID.charAt(0) == '-'){
					newCID = newCID.substring(1, newCID.length());
					signal = -1;
				}

				// TODO: HERE IS THE HARD-CLAUSE TRICK. CHECK WHETHER
				// WE NEED IT.
				// it is almost impossible for a non-zero-violation
				// clause to have such a big value...
				//if(currentWeight.get(newCID)*signal > 19){
				//	Learner.isHardMappings.put(newCID, true);
				//	c.weight = Config.hard_weight + 100;
				//	break;
				//}else
				c.weight += currentWeight.get(newCID) * signal;
			}
		}
		calcCosts();
		buildIndices();
	}

	/**
	 * Perform one sample of MC-SAT
	 * @param numFlips number of sampleSAT flips
	 */
	public double performMCSatStep(long numFlips){

		this.invalidateLowCost();

		clearSampledClauses();
//		initMRF(); // TODO(ericgribkoff) Why did I comment this out? It broke inference - need to retain clauses first!
		retainSatisfiedClauses();
		
		if (sampledClauses.size() == 0) {
//			printAllClauses();
//			UIMan.verbose(3,  "here");
			initMRF(); // Could end up here if MLN has no hard clauses, so initial "solution" found by WalkSAT
			           // is empty; reinit atoms randomly and hope we get some satisfied clauses. Can also get
			 		   // here if very few soft clauses and none get picked by MC-SAT in this round
		}
		
		UIMan.verbose(2, "    Retained #clauses = " + UIMan.comma(sampledClauses.size()) + 
				" out of " + UIMan.comma(clauses.size()) + " total clauses");
		
//		retainSomeGoodClauses();
		// TODO: CURRENTLY, CE FINDS ADDING THIS FUNCTION
		// MAY INFLUENCE THE SPEED OF SAMPLESAT. CHECK IT
		// IN THE FUTURE
		
		//TODO(ericgribkoff) Re-enable and make sure this UP step works.
		//unitPropagation();

		boolean sampleSatReturnVal = false;
		sampleSatReturnVal = sampleSAT(numFlips);
		
		if (!sampleSatReturnVal) {
			Stats.mcsatStepsWhereSampleSatFails += 1;
		}

		unfixAllAtoms();
		enableAllClauses();
		restoreLowTruth();
		
//		UIMan.verbose(3, "After SampleSAT:");
//		printSampledAtoms();

		
		updateAtomTruthTallies();

		this.recalcCost();

		return this.getCost();

	}


	public MRF simplifyWithHardUnits(LinkedHashSet<Integer> hardUnits) {
		// adj gives literal to clause map
		for (Integer lit: hardUnits) {
			if (adj.get(lit) != null) {
				for (GClause cee : adj.get(lit)) {
					if (cee.weight < 0) {
						continue;
					}
					if (!cee.isUnitClause()) {
						cee.ignoreAfterUnitPropagation = true;
					}
				}
			}
			if (adj.get(-lit) != null) {
				if (-lit == 3) {
					UIMan.println("here");
				}
				for (GClause cee : adj.get(-lit)) {
					if (cee.weight < 0) {
						continue;
					}
					int tmpLit[] = new int[cee.lits.length-1];
					int index = 0;
					for (int t : cee.lits) {
						if (t != -lit) {
					    	tmpLit[index] = t;
					    	index++;
						}
					}
					cee.lits = tmpLit;
				}
			}
		}
		
		MRF mrf = new MRF(mln);
		mrf.ownsAllAtoms = true;
		for (int i : coreAtoms) {
			GAtom n = atoms.get(i);
			mrf.atoms.put(n.id, n);
			mrf.addAtom(n.id);
		}
		
		for (GClause cee : clauses) {
		    if (!cee.ignoreAfterUnitPropagation) {
				if (cee.lits.length == 0 && cee.isHardClause()) {
					ExceptionMan
							.die("stopping here with an unsatisfiable hard clause");
				}
				mrf.clauses.add(cee);
			}
		}
		mrf.buildIndices();
		return mrf;
	}
	
	public MRF unitPropagateAndGetNewMRF() {
		//TODO(ericgribkoff) Potentially buggy
		return this;
//		unitPropagateGround();
//		
//		MRF mrf = new MRF(mln);
//		mrf.ownsAllAtoms = true;
//		for (int i : coreAtoms) {
//			GAtom n = atoms.get(i);
//			mrf.atoms.put(n.id, n);
//			mrf.addAtom(n.id);
//		}
//		LinkedHashMap<Integer, Boolean> hardUnitClauses = new LinkedHashMap<Integer,Boolean>();
////		if (cee.isUnitClause() && cee.isHardClause()) {
////			//if (!hardUnitClauses.contains(cee.lits[0])) {
////			//	hardUnitClauses.add(cee.lits[0]);
////				mrf.clauses.add(cee);
////			//}
////		}
////		else if (!cee.ignoreAfterUnitPropagation) {
////			mrf.clauses.add(cee);
////		}
//		for (GClause cee : clauses) {
////			if ((cee.isUnitClause() && cee.isHardClause()) || 
////					!cee.ignoreAfterUnitPropagation) {
////				mrf.clauses.add(cee);
////			}
//			if (cee.isUnitClause() && cee.isHardClause()) {
//				if (!hardUnitClauses.containsKey(Math.abs(cee.lits[0]))) {
//					hardUnitClauses.put(Math.abs(cee.lits[0]), (cee.lits[0] * cee.weight > 0));
//					mrf.clauses.add(cee);
//				    UIMan.verbose(3, "Adding " + cee);
//				} else {
//					if (hardUnitClauses.get(Math.abs(cee.lits[0])) == (cee.lits[0] * cee.weight > 0)) {
//						UIMan.verbose(3, "Skipping " + cee + " (already added)");
//					} else {
//						ExceptionMan.die("stopping here with an unsatisfiable hard clause\n" + 
//								"trying to add " + cee + " but already had its negation as a hard clause");
//					}
//				}
//			}
//			else if (!cee.ignoreAfterUnitPropagation) {
//				mrf.clauses.add(cee);
//			}
//		}
//		mrf.buildIndices();
//		return mrf;
//	}
//	
//	private void unitPropagateGround() {
//		Deque<Integer> Q = new ArrayDeque<Integer>();
//		LinkedHashSet<Integer> QIds = new LinkedHashSet<Integer>();
//		int lit;
//		// adj gives literal to clause map
//		for (GClause cee : clauses) {
//			if (Timer.hasTimedOut()) {
//				ExceptionMan.die("Tuffy timed out");
//			}
//			if (cee.isUnitClause() && cee.isHardClause()) {
//				lit = cee.lits[0];
//				cee.ignoreAfterUnitPropagation = true;
//				if(lit > 0){
//					if (cee.isPositiveClause()) {
//						if (!QIds.contains(Math.abs(lit))) {
//							Q.add(lit);
//							QIds.add(Math.abs(lit));
////							UIMan.verbose(3, "From " + cee + ", Adding to Q1: " + lit);
//						}
//						fixAtom(lit, true);
//					} else {
//						if (!QIds.contains(Math.abs(lit))) {
//							Q.add(-lit);
//							QIds.add(Math.abs(lit));
////							UIMan.verbose(3, "From " + cee + ", Adding to Q2: " + -lit);
//						}
//						fixAtom(lit, false);
//					}
//				}else{
//					if (cee.isPositiveClause()) {
//						if (!QIds.contains(Math.abs(lit))) {
//							Q.add(lit);
//							QIds.add(Math.abs(lit));
////							UIMan.verbose(3, "From " + cee + ", Adding to Q3: " + lit);
//						}
//						fixAtom(-lit, false);
//					} else {
//						if (!QIds.contains(Math.abs(lit))) {
//							Q.add(-lit);
//							QIds.add(Math.abs(lit));
////							UIMan.verbose(3, "From " + cee + ", Adding to Q4: " + -lit);
//						}
//						fixAtom(-lit, true);
//					}
//				}
//			}
//		}
//		while (!Q.isEmpty()) {
//			if (Timer.hasTimedOut()) {
//				ExceptionMan.die("Tuffy timed out");
//			}
//			lit = Q.pop();
////			UIMan.verbose(3, "Popped from Q: " + lit + "");
//			QIds.remove(Math.abs(lit));
//			ArrayList<GClause> tmp = adj.get(lit);
//			if (adj.get(lit) != null) {
//				//TODO(ericgribkoff) Make any difference to inference if cee has negative weight and is removed?
//				for (GClause cee : adj.get(lit)) {
//					for (int k : cee.lits) {
//							if (k != lit) {
//								adj.get(k).remove(cee);
//							}
//					}
////					UIMan.verbose(3, "Removing clause (unless hard unit clause) " + cee);
//					cee.ignoreAfterUnitPropagation = true;
//				}
//			}
//			ArrayList<Integer> aidToDelete = new ArrayList<Integer>(); 
//			if (adj.get(-lit) != null) {
//				for (GClause cee : adj.get(-lit)) {
//					if (cee.ignoreAfterUnitPropagation) {
//						continue;
//					}
//					if (cee.lits.length == 1) {
//						UIMan.verbose(3, "Clause " + cee + " is unsatisfiable");
//						continue;
//					}
//					int tmpLit[] = new int[cee.lits.length-1];
//					int index = 0;
//					for (int t : cee.lits) {
//						if (t != -lit) {
//					    	tmpLit[index] = t;
//					    	index++;
//						}
//					}
//					StringBuilder clauseStr = new StringBuilder();
//					clauseStr.append(tmpLit[0]);
//					for (int i = 1; i < tmpLit.length; i++) {
//						clauseStr.append(", ").append(tmpLit[i]);
//					}
////					UIMan.verbose(3, "Clause " + cee + " becomes: " + clauseStr);
//					cee.lits = tmpLit;
//					if (cee.isUnitClause() && cee.isHardClause()) {
//						int k = cee.lits[0];
//						if (k != -lit) {
//							if(k > 0){
//								if (cee.isPositiveClause()) {
//									// TODO(ericgribkoff): Moving away from this code anyways, but
//									// this shouldn't check for abs(k) - we can have -l and l
//									// and here we should catch this and return that the formulas
//									// are unsatisfiable. Currently this is not caught till after
//									// this unit prop routine finishes and the new MRF is being generated
//									// in unitPropagateAndGetNewMRF()
//									if (!QIds.contains(Math.abs(k))) {
//										Q.add(k);
//										QIds.add(Math.abs(k));
////										UIMan.verbose(3, "Adding to Q1: " + k);
//									}
//									fixAtom(k, true);
//								} else {
//									if (!QIds.contains(Math.abs(k))) {
//										Q.add(-k);
//										QIds.add(Math.abs(k));
////										UIMan.verbose(3, "Adding to Q2: " + -k);
//									}
//									fixAtom(k, false);
//								}
//							}else{
//								if (cee.isPositiveClause()) {
//									if (!QIds.contains(Math.abs(k))) {
//										Q.add(k);
//										QIds.add(Math.abs(k));
////										UIMan.verbose(3, "Adding to Q3: " + k);
//									}
//									fixAtom(-k, false);
//								} else {
//									if (!QIds.contains(Math.abs(k))) {
//										Q.add(-k);
//										QIds.add(Math.abs(k));
////										UIMan.verbose(3, "Adding to Q4: " + -k);
//									}
//									fixAtom(-k, true);
//								}
//							}
//						}
//						adj.get(k).remove(cee);
//						cee.ignoreAfterUnitPropagation = true;
//					}
//				}
//			}
//		}
	}
	
	/**
	 * Test if a clause is always true no matter how we flip flippable atoms.
	 * The existing implementation does not work, at least not when ownsAllAtoms is true
	 * in the non-partitioned case.
	 * @param gc the clause
	 * 
	 */
	protected boolean isAlwaysTrueNonPartititioned(GClause gc){
		int fixed = 0;
		for(int lit : gc.lits){
//			if(!ownsAtom(Math.abs(lit))){
				if(isTrueLit(lit)){
					return true;
				}
				fixed ++;
//			}
		}
		return fixed == gc.lits.length;
	}
	
	/**
	 * Try to satisfy as many clauses as possible with unit propagation.
	 * Used as a preprocessing step of SampleSAT, which tries to uniformly 
	 * sample among all zero-cost worlds.
	 */
	@SuppressWarnings("unused")
	private void unitPropagation(){
		/**
		 *  Satisfy negative clauses.
		 *  We have to make sure all literals are false.
		 */
		for(GClause cee : clauses){
			if(cee.dead || cee.isPositiveClause()) continue;
			if(isAlwaysTrueNonPartititioned(cee)) continue;
			for(int lit : cee.lits){
				if(lit > 0){
					fixAtom(lit, false);
				}else{
					fixAtom(-lit, true);
				}
			}
		}
		/**
		 *  Propagate to positive clauses.
		 *  We only need to fix one literal of each positive clause.
		 *  Keep sweeping the clauses until no fixing can be done in the whole round.
		 *  TODO: improve the efficiency of this procedure
		 */
		boolean done = false;
		while(!done){
			done = true;
			for(GClause cee : clauses){
				if(cee.dead || !cee.isPositiveClause()) continue;
				if(isAlwaysTrueNonPartititioned(cee)) continue;

				int numCand = 0, lastCand = 0;
				for(int lit : cee.lits){
					GAtom atom = atoms.get(Math.abs(lit));
					if(!atom.fixed){
						lastCand = lit;
						numCand ++;
						if(numCand > 1) break;
					}
				}
				if(numCand == 1){
					fixAtom(Math.abs(lastCand), lastCand>0);
					done = false;
				}
			}
		}
	}



}

