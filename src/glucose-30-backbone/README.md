# Glucose 3.0 Solver modified for Unit Propagation and Backbone Detection

This is a minor modification to the [Glucose 3.0 SAT Solver](http://www.labri.fr/perso/lsimon/glucose) that can be used to print out literals that have fixed values in the input formula, detected either by running Unit Propagation or by doing full Backbone Detection. The solver can also produce a list of pairs of equivalent literals: `x = y` or `x = NOT y`.

To detect whether a variable `x` is a backbone for a CNF SAT formula `F`, the solver sets `x = False` as an "assumption" and checks whether `F` is still satisfiable. For an `N` variable formula, this process involves up to `2*N` calls to the SAT solving routine. Much of the work during these calls, however, is shared and reused due to the use of the "assumptions" functionality of Glucose.

The output this solver is (optionally) used by TuffyLite to simplify the grounding of hard and soft constraints by exploiting structure imposed by hard constraints.

Please refer to the [original Glucose 3.0 solver](http://www.labri.fr/perso/lsimon/glucose) for details about the kinds of problems it can solve.

To compile, do: `cd core; make`. Note that a verison of `g++` at least as new as `4.4` may be needed, depending on your system. The code has been tested with versions `4.8` and `5.0`. Basic usage may be seen by running `glucose --help`. The new additions to Glucose are invoked using the following command-line options:

```
NEW OPTIONS:

  -printunits, -no-printunits             (default: off)
  -printbackbone, -no-printbackbone       (default: off)
  -printequiv, -no-printequiv             (default: off)
```

