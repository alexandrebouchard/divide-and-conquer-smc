package multilevel;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.distribution.BinomialDistribution;

import bayonet.distributions.Exponential;
import bayonet.distributions.Multinomial;
import briefj.OutputManager;
import briefj.collections.Counter;

import com.google.common.collect.Lists;



public class MultiLevelDcSmc
{
  private final MultiLevelDataset dataset;
  private final int nParticles;
  private final OutputManager output = new OutputManager();
  
  public void sample(Random rand)
  {
    recurse(rand, dataset.getRoot());
  }
  
  public MultiLevelDcSmc(MultiLevelDataset dataset, int nParticles)
  {
    this.dataset = dataset;
    this.nParticles = nParticles;
  }

  public static class ParticleApproximation
  {
    public final Particle [] particles;
    public final double [] probabilities;
    private ParticleApproximation(int nParticles)
    {
      this.particles = new Particle[nParticles];
      this.probabilities = new double[nParticles];
    }
    public Particle sample(Random rand)
    {
      int index = Multinomial.sampleMultinomial(rand, probabilities);
      return particles[index];
    }
  }
  
  public static class Particle
  {
    public final BrownianModelCalculator message;
    public final double variance;
    private Particle(BrownianModelCalculator message, double variance)
    {
      this.message = message;
      this.variance = variance;
    }
    public Particle(BrownianModelCalculator leaf)
    {
      this(leaf, Double.NaN);
    }
  }
  
  private ParticleApproximation recurse(Random rand, Node node)
  {
    Set<Node> children = dataset.getChildren(node);
    
    ParticleApproximation result;
    if (children.isEmpty())
      result = _leafParticleApproximation(rand, node);
    else
    {
      result = new ParticleApproximation(nParticles);
      List<ParticleApproximation> childrenApproximations = Lists.newArrayList();
      
      for (Node child : children)
        childrenApproximations.add(recurse(rand, child));
       
      for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
      {
        // sample a variance
        double variance = sampleVariance(rand);
        
        // build product sample from children
        double weight = 0.0;
        List<BrownianModelCalculator> sampledCalculators = Lists.newArrayList();
        for (ParticleApproximation childApprox : childrenApproximations)
        {
          sampledCalculators.add(childApprox.particles[particleIndex].message);
          weight += childApprox.probabilities[particleIndex];
        }
        
        // compute weight
        BrownianModelCalculator combined = BrownianModelCalculator.combine(sampledCalculators, variance);
        weight += combined.logLikelihood();
        for (BrownianModelCalculator childCalculator : sampledCalculators)
          weight = weight - childCalculator.logLikelihood();
        weight = weight - varianceRatio(variance);
        
        // add both qts to result
        result.particles[particleIndex] = new Particle(combined, variance);
        result.probabilities[particleIndex] = weight;
      }
    }
    
    // update log norm estimate
    // TODO
    
    // exp normalize
    Multinomial.expNormalize(result.probabilities);
    
    // monitor ESS
    double ess = SMCUtils.ess(result.probabilities);
    double relativeEss = ess/nParticles;
    output.printWrite("ess", "level", node.level, "nodeLabel", node.label, "ess", ess, "relativeEss", relativeEss);
    output.flush();
    
    // perform resampling
    return resample(rand, result, nParticles);
  }
  
  private static ParticleApproximation resample(Random rand, ParticleApproximation beforeResampling, int nParticles)
  {
    ParticleApproximation resampledResult = new ParticleApproximation(nParticles);
    Counter<Integer> resampledCounts = SMCUtils.multinomialSampling(rand, beforeResampling.probabilities, nParticles);
    // use the indices to create a new atoms array
    int currentIndex = 0;
    for (int resampledIndex : resampledCounts)
      for (int i = 0; i < resampledCounts.getCount(resampledIndex); i++)
        resampledResult.particles[currentIndex++] = beforeResampling.particles[resampledIndex];
    
    // reset particle logWeights
    double pr1overK = 1.0/nParticles;
    for (int k = 0; k < nParticles; k++)
      resampledResult.probabilities[k] = pr1overK;
    return resampledResult;
  }

  private double varianceRatio(double variance)
  {
    return 0; // assume we are sampling variance from prior for now
  }

  private double sampleVariance(Random rand)
  {
    return Exponential.generate(rand, 1.0);
  }

  private ParticleApproximation _leafParticleApproximation(Random rand, Node node)
  {
    ParticleApproximation result = new ParticleApproximation(nParticles);
    Datum observation = dataset.getDatum(node);
    
    for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
    {
      double unif = rand.nextDouble();
      BinomialDistribution bm = new BinomialDistribution(observation.numberOfTrials, unif);
      double logPi = Math.log(bm.getProbabilityOfSuccess());
      double transformed = transform(unif);
      double logWeight = logPi;
      BrownianModelCalculator leaf = BrownianModelCalculator.observation(new double[]{transformed}, 1, false);
      
      result.particles[particleIndex] = new Particle(leaf);
      result.probabilities[particleIndex] = logWeight;
    }
    
    return result;
  }

  private double transform(double unif)
  {
    return unif; // TODO: apply logistic here
  }
}
