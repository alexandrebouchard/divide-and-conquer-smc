package dc;

import tutorialj.Tutorial;

public class Doc
{
  /**
   * 
   * Summary
   * -------
   * 
   * Two implementations of Divide and Conquer Sequential Monte Carlo (DC SMC), described in our 
   * [arXiv preprint.](http://arxiv.org/abs/1406.4993)
   * 
   * 1. One implementation has a specific model hard coded in it, namely the hierarchical model 
   *    with a binomial emission model used in section 5.2 of the above pre-print.
   * 2. A second implementation offers convenient interfaces to apply the algorithm to other models. 
   *    This second implementation also offers distributed and parallel computing functionalities.
   *    This second implementation is used in section 5.3 of the above pre-print.
   * 
   * The paper explaining this method is under review. Please contact us if you would like to use this software.
   * 
   * 
   * Installation
   * ------------
   * 
   * There are three ways to install:
   * 
   * ### Integrate to a gradle script
   * 
   * Simply add the following lines (replacing 1.0.0 by the current version (see git tags)):
   * 
   * ```groovy
   * repositories {
   *  mavenCentral()
   *  jcenter()
   *  maven {
   *     url "http://www.stat.ubc.ca/~bouchard/maven/"
   *   }
   * }
   * 
   * dependencies {
   *   compile group: 'ca.ubc.stat', name: 'multilevelSMC', version: '1.0.0'
   * }
   * ```
   * 
   * ### Compile using the provided gradle script
   * 
   * - Check out the source ``git clone git@github.com:alexandrebouchard/multilevelSMC.git``
   * - Compile using ``gradle installApp``
   * - Add the jars in ``build/install/multilevelSMC/lib/`` into your classpath
   * 
   * ### Use in eclipse
   * 
   * - Check out the source ``git clone git@github.com:alexandrebouchard/multilevelSMC.git``
   * - Type ``gradle eclipse`` from the root of the repository
   * - From eclipse:
   *   - ``Import`` in ``File`` menu
   *   - ``Import existing projects into workspace``
   *   - Select the root
   *   - Deselect ``Copy projects into workspace`` to avoid having duplicates
   *   
   *   
   * Running DC SMC on the binary emission hierarchical model (implementation 1)
   * ---------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-simple.sh -inputData data/preprocessedNYSData.csv -nParticles 1000``. 
   * - Note: for the plots to work, you need to have R in your PATH variable (more precisely, Rscript) 
   * - Various output files are written in ``results/latest/``
   * 
   * The main interest of this implementation is for replicating the results in section 5.2 and 5.3 of 
   * our pre-print. To do so, see the following [separate public repository](https://github.com/alexandrebouchard/multilevelSMC-experiments) 
   * which contains the exact sets of options used to run the experiments as well as the plotting scripts.
   * To extend DC SMC to other models, use the second implementation instead, described next.
   * 
   * 
   * Running parallel/distributed DC SMC on the binary emission hierarchical model (implementation 2)
   * ------------------------------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv``
   * 
   * To run in a distributed fashion, simply issue the same command line on different machines. The machines will 
   * discover each other (via multicast) and distribute the work dynamically. For testing purpose, you can do this on
   * one machine (however, there is a better way to get multi-threading, see below).
   * 
   * The machines will only cooperate if all the command line options match exactly. You can check for example that running 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 2000`` will not cooperate with 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 1000``. You can also force groups 
   * of machines to avoid cooperation by using the ``-clusterSubGroup [integer]``
   * 
   * If you want the machines to wait each other, 
   * use ``-minimumNumberOfClusterMembersToStart [integer]``. 
   * Other machines can still join after the wait period. This waiting period option was used for wall-clock 
   * running time analysis purpose.
   * 
   * To use multiple threads within one machine, use ``-nThreadsPerNode [integer]``. This can be used in conjunction with 
   * a distributed computation, or without. 
   * 
   * When the number of nodes in the DC-SMC tree is much larger than the number of threads x nodes, it is useful to 
   * coarsen the granularity of the subtasks. To do so, you can use ``-maximumDistributionDepth [integer]``, which alters the 
   * way subtasks are created as follows: instead of the basic initial tasks being the leaves of the tree, they are the 
   * subtrees rooted at a depth from the root given by ``maximumDistributionDepth``. 
   */
  @Tutorial(startTutorial = "README.md", showSource = false)
  public void installInstructions()
  {
  }
  
  // Node should have a reproducible hashcode
  // push maven!
 
  
}
