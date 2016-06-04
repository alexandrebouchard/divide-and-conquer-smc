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
   * - Run the software using ``build/install/multilevelSMC/bin/multilevelSMC -inputData data/preprocessedNYSData.csv -nParticles 1000``. 
   * - Note: for the plots to work, you need to have R in your PATH variable (more precisely, Rscript) 
   * - Various output files are written in ``results/latest/``
   * 
   * 
   * Running parallel/distributed DC SMC on the binary emission hierarchical model (implementation 2)
   * ------------------------------------------------------------------------------------------------
   * 
   * 
   * 
   */
  @Tutorial(showSource = false)
  public void installInstructions()
  {
  }
  
 
  
}
