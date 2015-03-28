package dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;





public final class DCRecursion
{
  
  public static <P> ParticlePopulation<P> dcRecurse(
      final Random random,
      final DCOptions options,
      final List<ParticlePopulation<P>> childrenPopulations,
      final DCProposal<P> proposal,
      final Collection<DCProcessor<P>> processors)
  {
    ParticlePopulation<P> result = dcPropose(random, options.nParticles, childrenPopulations, proposal);
    for (DCProcessor<P> processor : processors)
      processor.process(result, childrenPopulations);
    final double relativeESS = result.getRelativeESS();
    if (relativeESS < options.relativeEssThreshold)
      result = result.resample(random, options.resamplingScheme);
    return result;
  }
  
  private static <P> ParticlePopulation<P> dcPropose(
    final Random random,
    final int nParticles,
    final List<ParticlePopulation<P>> childrenPopulations,
    final DCProposal<P> proposal)
  {
    final int nChildren = childrenPopulations.size();
    if (nChildren != childrenPopulations.size())
      throw new RuntimeException();
    
    final List<P> particles = new ArrayList<>(nParticles);
    final double [] logWeights = new double[nParticles];

    for (final ParticlePopulation<P> childPopulation : childrenPopulations)
      if (childPopulation.nParticles() != nParticles)
        throw new RuntimeException();
    
    for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
    {
      final List<P> childrenParticles = new ArrayList<>(nChildren);
      double childrenWeightProduct = 1.0;
      for (final ParticlePopulation<P> childPopulation : childrenPopulations)
      {
        childrenParticles.add(childPopulation.particles.get(particleIndex));
        childrenWeightProduct *= childPopulation.getNormalizedWeight(particleIndex);
      }
        
      final Pair<Double, P> proposed = proposal.propose(random, childrenParticles);
      logWeights[particleIndex] = Math.log(childrenWeightProduct) + proposed.getLeft();
      
      particles.add(proposed.getRight());
    }
    
    final double childrenSumLogScaling = childrenPopulations
        .stream()
        .mapToDouble(p -> p.logScaling)
        .sum();
    
    return ParticlePopulation.buildDestructivelyFromLogWeights(logWeights, particles, childrenSumLogScaling);
  }
  
  private DCRecursion() {}
}
