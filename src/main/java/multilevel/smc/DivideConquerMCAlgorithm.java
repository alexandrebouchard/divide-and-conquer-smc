package multilevel.smc;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import multilevel.Node;
import multilevel.io.Datum;
import multilevel.io.MultiLevelDataset;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;


import bayonet.distributions.Exponential;
import bayonet.distributions.Multinomial;
import bayonet.distributions.Normal;
import bayonet.distributions.Random2RandomGenerator;
import bayonet.math.SpecialFunctions;
import bayonet.rplot.PlotHistogram;
import briefj.OutputManager;
import briefj.collections.Counter;
import briefj.opt.Option;
import briefj.run.Results;

import com.google.common.collect.Lists;



public class DivideConquerMCAlgorithm
{
  private final MultiLevelDataset dataset;
  private final int nParticles;
  private final OutputManager output = new OutputManager();
  private final MultiLevelDcSmcOptions options;
  
  
  public static class MultiLevelDcSmcOptions
  {
    @Option public int nParticles = 1000;
    @Option public int levelCutOffForOutput = 3;
    @Option public double variancePriorRate = 1.0;
    @Option public boolean useTransform = true;
    @Option public boolean useBetaProposal = true;
    @Option public int nPlotPoints = 10000;
  }
  
  public void sample(Random rand)
  {
    recurse(rand, dataset.getRoot());
  }
  
  public DivideConquerMCAlgorithm(MultiLevelDataset dataset, MultiLevelDcSmcOptions options)
  {
    this.dataset = dataset;
    this.nParticles = options.nParticles;
    this.options = options;
    output.setOutputFolder(Results.getResultFolder());
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
    public final Node node;
    public final BrownianModelCalculator message;
    public final List<BrownianModelCalculator> childrenMessages; // used to compute delta statistics
    public final List<Node> childrenNodes;
    public final double variance;
    private Particle(BrownianModelCalculator message, double variance, List<BrownianModelCalculator> childrenMessages, Node node, List<Node> childrenNodes)
    {
      this.message = message;
      this.variance = variance;
      this.childrenMessages = childrenMessages;
      this.node = node;
      this.childrenNodes = childrenNodes;
    }
    @SuppressWarnings("unchecked")
    public Particle(BrownianModelCalculator leaf, Node node)
    {
      this(leaf, Double.NaN, Collections.EMPTY_LIST, node, Collections.EMPTY_LIST);
    }
    public double sampleValue(Random rand)
    {
      return Normal.generate(rand, message.message[0], message.messageVariance);
    }

  }
  
  /**
   * If there are C children, return an array of joint samples of size C+1, where the last
   * entry is the root, and the rest are children
   * @param rand
   * @param p
   * @return
   */
  public double[] sampleChildrenJointly(Random rand, Particle p)
  {
    double rootSample = p.sampleValue(rand);
    int size = p.childrenMessages.size();
    double[] result = new double[size+1];
    
    for (int c = 0; c < size; c++)
    {
      BrownianModelCalculator updatedCalculator = p.message.combine(p.message, p.childrenMessages.get(c), p.variance, 0.0, false);
      result[c] = Normal.generate(rand, updatedCalculator.message[0], updatedCalculator.messageVariance);
    }
    result[size] = rootSample;
    
    return result;
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
        childrenApproximations.add(recurse(rand, child)); //BriefLists.concat(path,child)));
       
      for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
      {
        // sample a variance
        double variance = sampleVariance(rand);
        
        // build product sample from children
        double weight = 0.0;
        List<BrownianModelCalculator> sampledCalculators = Lists.newArrayList();
        List<Node> childrenNode = Lists.newArrayList();
        for (ParticleApproximation childApprox : childrenApproximations)
        {
          sampledCalculators.add(childApprox.particles[particleIndex].message);
          weight += childApprox.probabilities[particleIndex];
          childrenNode.add(childApprox.particles[particleIndex].node);
        }
        
        // compute weight
        BrownianModelCalculator combined = BrownianModelCalculator.combine(sampledCalculators, variance);
        weight += combined.logLikelihood();
        for (BrownianModelCalculator childCalculator : sampledCalculators)
          weight = weight - childCalculator.logLikelihood();
        weight = weight + varianceRatio(variance);
        
        // add both qts to result
        result.particles[particleIndex] = new Particle(combined, variance, sampledCalculators, node, childrenNode);
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
    output.printWrite("ess", "level", node.level(), "nodeLabel", node.toString(), "ess", ess, "relativeEss", relativeEss);
    output.flush();
    
    // perform resampling
    result = resample(rand, result, nParticles);
    
    // report statistics on node mean, var
    if (node.level() < options.levelCutOffForOutput)
      printNodeStatistics(rand, node, result);
    
    // report statistics on deltas
    if (node.level() + 1 < options.levelCutOffForOutput && !children.isEmpty())
      printDeltaNodeStatistics(rand, node, result);
    
    return result;
  }
  
  private void printDeltaNodeStatistics(Random rand, Node node,
      ParticleApproximation result)
  {
    Set<Node> children = dataset.getChildren(node);
    int nChildren = children.size();
    
    double [][] deltaSamples = new double[nChildren][options.nPlotPoints];
    DescriptiveStatistics [] stats = new DescriptiveStatistics[options.nPlotPoints];
    for (int c = 0; c < nChildren; c++)
      stats[c] = new DescriptiveStatistics();
    
    for (int i = 0; i < options.nPlotPoints; i++)
    {
      Particle p = result.particles[i % nParticles];
      double [] jointSample = sampleChildrenJointly(rand, p);
      double transformedRoot = inverseTransform(jointSample[nChildren]);
      
      for (int c = 0; c < nChildren; c++)
      {
        double transformedChild = inverseTransform(jointSample[c]);
        double delta = transformedChild - transformedRoot;
        deltaSamples[c][i] = delta;
        stats[c].addValue(delta);
      }
    }
    File plotsFolder = Results.getFolderInResultFolder("histograms");
    
    for (int c = 0; c < nChildren; c++)
    {
      String pathStr = result.particles[0].childrenNodes.get(c).toString();
      new PlotHistogram(deltaSamples[c]).toPDF(new File(plotsFolder, pathStr + "_delta.pdf"));
      output.printWrite("deltaStats", "path", pathStr, "deltaMean", stats[c].getMean(), "deltaSD", stats[c].getStandardDeviation());
    }
    output.flush();
  }

  private void printNodeStatistics(Random rand, Node node, ParticleApproximation result)
  {
    Set<Node> children = dataset.getChildren(node);
    
    DescriptiveStatistics meanStats = new DescriptiveStatistics();
    double [] meanSamples = new double[options.nPlotPoints];
    double [] varianceSamples = children.isEmpty() ? null : new double[options.nPlotPoints];
    DescriptiveStatistics varStats = children.isEmpty() ? null : new DescriptiveStatistics();
    for (int i = 0; i < options.nPlotPoints; i++)
    {
      Particle p = result.particles[i % nParticles];
      double meanPoint = inverseTransform(p.sampleValue(rand));
      meanStats.addValue(meanPoint);
      meanSamples[i] = meanPoint;
      if (varianceSamples != null)
      {
        varianceSamples[i] = p.variance;
        varStats.addValue(p.variance);
      }
    }
    File plotsFolder = Results.getFolderInResultFolder("histograms");
    String pathStr = node.toString();
    new PlotHistogram(meanSamples).toPDF(new File(plotsFolder, pathStr + "_logisticMean.pdf"));
    output.printWrite("meanStats", "path", pathStr, "meanMean", meanStats.getMean(), "meanSD", meanStats.getStandardDeviation());
    if (varianceSamples != null)
    {
      new PlotHistogram(varianceSamples).toPDF(new File(plotsFolder, pathStr + "_var.pdf"));
      output.printWrite("varStats", "path", pathStr, "varMean", varStats.getMean(), "varSD", varStats.getStandardDeviation());
    }
    output.flush();    
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
    return Exponential.generate(rand, options.variancePriorRate );
  }

  private ParticleApproximation _leafParticleApproximation(Random rand, Node node)
  {
    ParticleApproximation result = new ParticleApproximation(nParticles);
    Datum observation = dataset.getDatum(node);
    
    // use a beta distributed proposal
    BetaDistribution beta = options.useBetaProposal  ? 
        new BetaDistribution(
            new Random2RandomGenerator(rand), 
            1 + observation.numberOfSuccesses, 
            1 + (observation.numberOfTrials - observation.numberOfSuccesses), 
            BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY) :
        null;
    for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
    {
      double proposed = options.useBetaProposal ? 
        beta.sample() :
        rand.nextDouble();

      double logPi = logBinomialPr(observation.numberOfTrials, observation.numberOfSuccesses, proposed);
      double transformed = transform(proposed);
      double logWeight = options.useBetaProposal ? 
          0.0 : // true up to a constant (because we have a ratio of a binomial to a beta
                // TODO: compute that constant to get correct Z estimate in the future
          logPi;// logPi - 0.0

      BrownianModelCalculator leaf = BrownianModelCalculator.observation(new double[]{transformed}, 1, false);
      
      result.particles[particleIndex] = new Particle(leaf, node);
      result.probabilities[particleIndex] = logWeight;
    }
    
    // TODO: note expnorm needs to be done by the caller, could lead to problem later
    return result;
  }
  
  private double logBinomialPr(int nTrials, int nSuccesses, double prOfSuccess)
  {
    if (nTrials < 0 || nSuccesses < 0 || nSuccesses > nTrials || prOfSuccess < 0 || prOfSuccess > 1)
      throw new RuntimeException();
    return SpecialFunctions.logBinomial(nTrials, nSuccesses) 
      + nSuccesses             * Math.log(prOfSuccess) 
      + (nTrials - nSuccesses) * Math.log(1.0 - prOfSuccess);
  }

  private double transform(double numberOnSimplex)
  {
    if (options.useTransform )
      return SpecialFunctions.logit(numberOnSimplex);
    else
      return numberOnSimplex;
  }
  
  private double inverseTransform(double realNumber)
  {
    if (options.useTransform)
      return SpecialFunctions.logistic(realNumber);
    else
      return realNumber;
  }
}
