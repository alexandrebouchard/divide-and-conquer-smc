package prototype.adaptor;

import java.io.File;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.distribution.BetaDistribution;

import prototype.Node;
import prototype.io.Datum;
import prototype.io.MultiLevelDataset;
import prototype.smc.DivideConquerMCAlgorithm.Particle;
import bayonet.distributions.Random2RandomGenerator;
import briefj.opt.Option;
import dc.DCProposal;
import dc.DCProposalFactory;



public final class MultiLevelProposalFactory implements DCProposalFactory<Particle,Node>
{
  @Option(required = true)
  public File dataFile = null;
  
  @Option
  public double variancePrior = 1.0;
  
  private transient MultiLevelDataset _dataset;
  public MultiLevelDataset getDataset()
  {
    if (_dataset == null)
      _dataset = new MultiLevelDataset(dataFile);
    return _dataset;
  }

  @Override
  public DCProposal<Particle> build(Random random, Node currentNode,
      List<Node> childrenNodes)
  {
    if (childrenNodes.isEmpty())
    {
      Datum observation = getDataset().getDatum(currentNode);
      BetaDistribution beta = new BetaDistribution(
          new Random2RandomGenerator(random), 
          1 + observation.numberOfSuccesses, 
          1 + (observation.numberOfTrials - observation.numberOfSuccesses), 
          BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
      return new MultiLevelLeafProposal(beta, observation.numberOfTrials, observation.numberOfSuccesses, currentNode);
    }
    else
      return new MultiLevelInternalProposal(variancePrior, currentNode, childrenNodes);
  }
}