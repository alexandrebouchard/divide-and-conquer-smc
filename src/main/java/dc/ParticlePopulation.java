package dc;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.stream.DoubleStream;

import bayonet.distributions.Multinomial;
import bayonet.smc.ResamplingScheme;



public final class ParticlePopulation<P> implements Serializable
{
  private static final long serialVersionUID = 1L;
  
  public final List<P> particles;
  private final double [] normalizedWeights;
  
  /**
   * Note: not the same as logNormEstimate() !
   * Useful to get unified weight update, not matter if resampling
   * was done last time or not.
   */
  public final double logScaling;
  
  public static <P> ParticlePopulation<P> buildDestructivelyFromLogWeights(
      double [] logWeights, 
      final List<P> particles, 
      final double logScaling)
  {
    double logWeightsScaling = Multinomial.expNormalize(logWeights);
    return new ParticlePopulation<>(particles, logWeights, logScaling + logWeightsScaling);
  }
  
  public static <P> ParticlePopulation<P> buildEquallyWeighted(
      final List<P> particles, 
      final double logScaling)
  {
    return new ParticlePopulation<>(particles, null, logScaling);
  }
  
  private ParticlePopulation(
      final List<P> particles, 
      final double[] normalizedWeights,
      final double logScaling)
  {
    this.particles = particles;
    this.normalizedWeights = normalizedWeights;
    this.logScaling = logScaling;
  }

  public double getNormalizedWeight(final int index)
  {
    if (normalizedWeights == null)
      return 1.0 / nParticles();
    return normalizedWeights[index];
  }
  
  public int nParticles()
  {
    return particles.size();
  }
  
  public double logNormEstimate()
  {
    return logScaling - Math.log(nParticles());
  }
  
  public ParticlePopulation<P> resample(
      final Random random, 
      final ResamplingScheme resamplingScheme)
  {
    final List<P> resampled = resamplingScheme.resample(random, normalizedWeights, particles);
    return ParticlePopulation.buildEquallyWeighted(resampled, logScaling);
  }

  public double getESS()
  {
    return 1.0 / DoubleStream.of(normalizedWeights).map(w -> w*w).sum();
  }
}