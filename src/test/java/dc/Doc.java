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
   * Running parallel and distributed DC SMC on the binary emission hierarchical model (implementation 2)
   * ----------------------------------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv``
   * 
   * ### Distributed and parallel computing options
   * 
   * To run in a distributed fashion, simply issue the same command line on different machines. The machines will 
   * discover each other (via multicast) and distribute the work dynamically. You can see in the output a variable 
   * ``nWorkers=[integer]`` showing the number of nodes cooperating. For testing purpose, you can do this on
   * one machine by opening two terminals (however, there is a better way to get multi-threading, see below).
   * 
   * The machines will only cooperate if all the command line options match exactly. You can check for example that running 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 2000`` will not cooperate with 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 1000``. You can also force groups 
   * of machines to avoid cooperation by using the ``-clusterSubGroup [integer]``
   * 
   * To use multiple threads within one machine, use ``-nThreadsPerNode [integer]``. This can be used in conjunction with 
   * a distributed computation, or without. 
   * 
   * 
   * ### Additional options
   * 
   * If you want the machines to wait each other, 
   * use ``-minimumNumberOfClusterMembersToStart [integer]``. This will cause machines to wait to start until this number 
   * of machines is gathered OR until ``maximumTimeToWaitInMinutes`` minutes has elapsed. 
   * Whether these options is used or not, machines can always join at any point in the execution of the algorithm. 
   * The waiting options were used for wall-clock 
   * running time analysis purpose. In most cases, these options can be ignored.
   * 
   * When the number of nodes in the DC-SMC tree is much larger than the number of threads times the number of nodes, 
   * it is useful to 
   * coarsen the granularity of the subtasks. To do so, you can use ``-maximumDistributionDepth [integer]``, which alters the 
   * way subtasks are created as follows: instead of the basic initial tasks being the leaves of the tree, they are the 
   * subtrees rooted at a depth from the root given by ``maximumDistributionDepth``. The subtrees under are computed 
   * serially within each node/thread. 
   * 
   * 
   * Using parallel and distributed DC SMC with your model
   * -----------------------------------------------------
   * 
   * First, build a class to encode each individual particle. The only requirement is that it should implement 
   * ``Serializable``. This will become generic type ``P`` in the following. 
   * 
   * Second, build a class to encode nodes in the tree. The only requirement is that you should override hashcode 
   * and equals to ensure that each node in the tree be unique (i.e. not .equals(..) with any other node, 
   * they will be inserted in a hashtable), AND reproducible (i.e. the default implementation of hashcode will 
   * depend on the memory location and will be different from machine to machine). A reasonable default implementation 
   * is in ``multilevel.Node``. This will become generic type ``N`` in the following. 
   * 
   * Next, the main step consists in providing code that proposes, i.e. merges sub-populations. This is done by 
   * creating a class only required to implement a single method, namely:
   */
  @Tutorial(startTutorial = "README.md", showSource = false, nextStep = DCProposal.class)
  public void instructions() {}
  
  /**
   * Next, one should create a factory for the proposal defined above. Again, this is done by creating a 
   * class only required to implement a single method, namely:
   */
  @Tutorial(showSource = false, nextStep = DCProcessorFactory.class)
  public void instructionsContinued() {}
  
  // push maven!
  // add some basic doc in the separate repo on experiments multilevelSMC-experiments
 
  
}
