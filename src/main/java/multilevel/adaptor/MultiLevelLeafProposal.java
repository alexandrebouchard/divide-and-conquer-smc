package multilevel.adaptor;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.distribution.BetaDistribution;

import bayonet.math.SpecialFunctions;
import multilevel.Node;
import multilevel.smc.BrownianModelCalculator;
import multilevel.smc.DivideConquerMCAlgorithm;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import dc.DCProposal;



public final class MultiLevelLeafProposal implements DCProposal<Particle>
{
  private final BetaDistribution beta;
  private final int numberOfTrials;
  private final int numberOfSuccesses;
  private final Node node;
  
  MultiLevelLeafProposal(BetaDistribution beta, int numberOfTrials,
      int numberOfSuccesses, Node node)
  {
    this.beta = beta;
    this.numberOfTrials = numberOfTrials;
    this.numberOfSuccesses = numberOfSuccesses;
    this.node = node;
  }

  @Override
  public Pair<Double, Particle> propose(Random random, List<Particle> childrenParticles)
  {
    final double proposed = beta.sample();
    final double logPi = DivideConquerMCAlgorithm.logBinomialPr(numberOfTrials, numberOfSuccesses, proposed);
    final double transformed = SpecialFunctions.logit(proposed);
    final double logWeight = 0.0;
    final BrownianModelCalculator leaf = BrownianModelCalculator.observation(new double[]{transformed}, 1, false);
    return Pair.of(logWeight, new Particle(leaf, node, logPi));  
  }
  
  public String toString()
  {
    return "leafProposal[" + node + "]";
  }
}
