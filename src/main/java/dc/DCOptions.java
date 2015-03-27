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
}