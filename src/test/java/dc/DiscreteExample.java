package dc;

import static dc.TestUtilities.computeExactLogZ;
import static dc.TestUtilities.perfectBinaryTree;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import bayonet.graphs.DirectedTree;
import briefj.OutputManager;
import briefj.run.Mains;
import briefj.run.Results;
import prototype.Node;
import xlinear.DenseMatrix;



public class DiscreteExample implements Runnable
{
  
  public static void main(String [] args)
  {
    Mains.instrumentedRun(args, new DiscreteExample());
  }

  @Override
  public void run()
  {
    DenseMatrix transition = Doc.transition;
    DenseMatrix prior = Doc.prior;
    
    OutputManager output = new OutputManager();
    output.setOutputFolder(Results.getResultFolder());
    
    final DirectedTree<Node> tree = perfectBinaryTree(3);
    final double exactLogZ = computeExactLogZ(transition, prior, tree, (node) -> 0);
    output.write("true-log-Z", "logZ", exactLogZ);
    DCProposalFactory<Integer, Node> proposalFactory = 
        Doc.markovChainNaiveProposalFactory(transition, prior);
    
    DCOptions options = new DCOptions();
//    options.nThreadsPerNode = 4; 
//    options.nParticles = 1_000_000;
//    options.masterRandomSeed = 31; // Note: given the seed, output is deterministic even if distributed and/or parallelized
//    options.resamplingScheme = ResamplingScheme.MULTINOMIAL; // currently supported: STRATIFIED and MULTINOMIAL (default)
//    options.relativeEssThreshold = 0.5;
    
    for (int nParticles = 10; nParticles < 100_000; nParticles *= 2)
    {
      SummaryStatistics mse = new SummaryStatistics();
      for (int trial = 0; trial < 100; trial++)
      {
        options.masterRandomSeed = 31 + 100*trial;
        options.nParticles = nParticles;
        DistributedDC<Integer, Node> ddc = DistributedDC.createInstance(options, proposalFactory, tree);
        ddc.start();
        final double logZEstimate = ddc.getRootPopulation().logNormEstimate();
        final double squareError = (logZEstimate - exactLogZ) * (logZEstimate - exactLogZ);
        mse.addValue(squareError);
        output.write("details", "nParticles", nParticles, "trial", trial, "logZEstimate", logZEstimate, "trueLogZ", exactLogZ);
      }
      output.write("mse", "nParticles", nParticles, "mse");
    }
    
    // start based on Doc
    
    // repeat to get Var Z estimates
    
    // check if get square root convergence
    
    // test different matrices? codon matrix?
    
    // then, reimplement a std smc, possibly with different preorder/postorder options
    
    // See notes in ICML notebook for next steps
    
    // PS: also ask collabs to test code on their computer?
    
    output.close();
  }

}
