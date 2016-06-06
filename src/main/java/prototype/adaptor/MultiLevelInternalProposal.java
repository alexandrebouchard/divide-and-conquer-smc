package prototype.adaptor;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;

import prototype.Node;
import prototype.smc.BrownianModelCalculator;
import prototype.smc.DivideConquerMCAlgorithm.Particle;
import bayonet.distributions.Exponential;

import com.google.common.collect.Lists;

import dc.DCProposal;



public final class MultiLevelInternalProposal implements DCProposal<Particle>
{
  private final double variancePrior;
  private final Node node;
  private final List<Node> childrenNode;
  
  MultiLevelInternalProposal(double variancePrior, Node node,
      List<Node> childrenNode)
  {
    this.variancePrior = variancePrior;
    this.node = node;
    this.childrenNode = childrenNode;
  }

  @Override
  public Pair<Double, Particle> propose(Random random,
      List<Particle> childrenParticles)
  {
    final double variance = Exponential.generate(random, variancePrior);
    List<BrownianModelCalculator> sampledCalculators = Lists.newArrayList();
    double descParticleObsLogl = 0.0;
    double descVar = Exponential.logDensity(variance, variancePrior);
    for (Particle childParticle : childrenParticles)
    {
      sampledCalculators.add(childParticle.message);
      descParticleObsLogl += childParticle.descendentObservationLogLikelihood;
      descVar += childParticle.descendentVarianceDensity;
    }
    
    BrownianModelCalculator combined = BrownianModelCalculator.combine(sampledCalculators, variance);
    double combinedLogLikelihood = combined.logLikelihood();
    double logWeight = combinedLogLikelihood;
    
    for (BrownianModelCalculator childCalculator : sampledCalculators)
      logWeight = logWeight - childCalculator.logLikelihood();
    
    Particle newParticle = new Particle(combined, variance, sampledCalculators, node, childrenNode, descParticleObsLogl, descVar);
    return Pair.of(logWeight, newParticle);
  }
  
  public String toString()
  {
    return "internalProposal[" + node + "]";
  }
}