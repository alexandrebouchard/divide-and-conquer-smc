package dc;

import java.io.Serializable;

import bayonet.math.NumericalUtils;
import bayonet.smc.ResamplingScheme;
import briefj.opt.Option;



public class DCOptions implements Serializable
{
  private static final long serialVersionUID = 1L;
  
  @Option
  public long masterRandomSeed = 31;

  @Option
  public int nParticles = 1000;
  
  @Option
  public ResamplingScheme resamplingScheme = ResamplingScheme.MULTINOMIAL;
  
  @Option(gloss = "Resample when the ess is below this value.")
  public double relativeEssThreshold = 1.0 + NumericalUtils.THRESHOLD;
  
  @Option(gloss = "Set to different values if otherwise identical runs "
      + "should be prevented from communicating.")
  public int clusterSubGroup = 1;
  
  @Option
  public int nThreadsPerNode = 1;
  
  @Option(gloss = "Wait to have a certain number of members to the cluster before starting.")
  public int minimumNumberOfClusterMembersToStart = 1;
  
  @Option(gloss = "Max time to wait for other members to join, in minute.")
  public int maximumTimeToWaitInMinutes = 1;
  
  @Option
  public int indexInCluster = 1;  // see below

  /**
   * Warning: this constant should match exactly with the name of the field above.
   */
  static String INDEX_IN_CLUSTER_FIELD_NAME = "indexInCluster";
}