# TuffyLite

TuffyLite is a modified version of the open source [Tuffy solver](http://hazy.cs.wisc.edu/hazy/tuffy) for performing inference with Markov Logic Networks (MLNs). Specifically, it is an inference-only variant of [Tuffy version 0.3](http://hazy.cs.wisc.edu/hazy/tuffy/download/tuffy-src-0.3-mar2014.zip) implementing the changes described in the following paper:

> [Markov Logic Networks for Natural Language Question Answering](http://arxiv.org/abs/1507.03045). Tushar Khot, Niranjan Balasubramanian, Eric Gribkoff, Ashish Sabharwal, Peter Clark, Oren Etzioni. StarAI-2015, 5th International Workshop on Statistical Relational AI, Amsterdam, The Netherlands, July 2015.

For more information about MLNs and the kinds of problems Tuffy is suitable for, please refer to the original Tuffy solver. The main changes to Tuffy implemented in TuffyLite are described below.

This project also includes minor modifications to the [Glucose 3.0 SAT Solver](http://www.labri.fr/perso/lsimon/glucose) to identify variables with fixed values by unit propagation, backbone detection, or equivalence detection. This information is (optionally) used by TuffyLite iteratively to simplify and often vastly reduce the size of the grounding of hard and soft constraints by exploiting structure imposed by hard constraints.


## List of Modifications

The main additions to Tuffy include:

* Iterative Unit Propagation and Backbone Detection when grounding an MLN: after grounding each hard rule, we run unit propagation (or, optionally, use backbone detection) to identify literals with fixed values, simplify the current grounding, and reduce the size of the groundings of subsequent hard and soft rules. Please see the above referenced paper for more details.

* Re-implementation of MC-SAT/SampleSAT algorithm for sampling from a ground MRF: some parts of the code in Tuffy had diverged from the standard SampleSAT algorithm, and the reimplementation yielded improved inference quality on some problems.

* Ability to run the code completely deterministically: random number seeds for Java and Postgres are now config options.

* Ability to specify a time limit.

* Bug fix when grounding existential quantifiers.

* Several output file options, including writing the ground MRF in human-readable or WCNF file format.

* Some other minor changes to default config options, disabling some heuristics such as "greedy throttling" which can make the underlying SampleSAT algorithm non-uniform.

On the down side, TuffyLite focuses mainly on the inference aspect, especially marginal inference, and currently does not support the following:

* No weight learning.

* No partitioned grounding and inference.


## Contact

Please contact [Erik Gribkoff](http://homes.cs.washington.edu/~eagribko) or [Ashish Sabharwal](http://ashishs.people.allenai.org) if you have any questions or comments.

