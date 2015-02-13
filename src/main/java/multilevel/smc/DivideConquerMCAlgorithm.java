package multilevel.smc;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import multilevel.Node;
import multilevel.io.Datum;
import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelBMTreeFactor;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import bayonet.distributions.Exponential;
import bayonet.distributions.Multinomial;
import bayonet.distributions.Normal;
import bayonet.distributions.Random2RandomGenerator;
import bayonet.distributions.Uniform;
import bayonet.math.NumericalUtils;
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
  private final MultiLevelModelOptions modelOptions;
  
  public static class MultiLevelModelOptions
  {
    @Option public boolean useTransform = true;
    @Option public boolean useUniformVariance = false;
    @Option public double maxVarianceIfUniform = 5.0;
    @Option public double variancePriorRateIfExponential = 1.0;
  }
  
  public static class MultiLevelDcSmcOptions
  {
    @Option public int nParticles = 1000;
    @Option public int levelCutOffForOutput = 2;
    @Option public boolean useBetaProposal = true;
    @Option public double essThreshold = 1.0;
  }
  
  public ParticleApproximation dc_sample(Random rand)
  {
    if (options.essThreshold != 1.0)
      throw new RuntimeException();
    ParticleApproximation result =  recurse(rand, dataset.getRoot());
    output.printWrite("logZ", "estimate", result.logZEstimate);
    output.flush();
    return result;
  }
  
  public DivideConquerMCAlgorithm(MultiLevelDataset dataset, MultiLevelDcSmcOptions options, MultiLevelModelOptions modelOptions)
  {
    this.modelOptions = modelOptions;
    this.dataset = dataset;
    this.nParticles = options.nParticles;
    this.options = options;
    output.setOutputFolder(Results.getResultFolder());
  }

  public static class ParticleApproximation  implements LogDensityApprox
  {
    public double logZEstimate = 0.0;
    public boolean resampled = false;
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
    @Override
    public double sampleNextLogDensity(Random random)
    {
      int index = Multinomial.sampleMultinomial(random, probabilities);
      Particle rootParticle =  particles[index];
      return rootParticle.logDensity();
    }
  }
  
  public static interface LogDensityApprox
  {
    public double sampleNextLogDensity(Random random);
  }
  
  public class StandardParticleApproximation implements LogDensityApprox
  {
    public boolean resampled = false;
    public final List<Map<Node, Particle>> particles;
    public final double [] weights;
    
    /**
     * Initialization
     * @param nParticles
     */
    private StandardParticleApproximation(int nParticles, boolean initialization)
    {
      this.particles = Lists.newArrayList();
      this.weights = new double[nParticles];
      if (initialization)
      {
        resampled = true;
        for (int i = 0; i < nParticles; i++)
        {
          this.particles.add(new LinkedHashMap<Node, DivideConquerMCAlgorithm.Particle>());
          this.weights[i] = 1.0/((double) nParticles);
        }
      }
    }
    
    public Map<Node,Particle> sample(Random random)
    {
      int index = Multinomial.sampleMultinomial(random, weights);
      return particles.get(index);
    }
    
    public double sampleNextLogDensity(Random random)
    {
      int index = Multinomial.sampleMultinomial(random, weights);
      Particle rootParticle =  particles.get(index).get(dataset.getRoot());
      return rootParticle.logDensity();
    }

  }
  
  public static class Particle
  {
    public final Node node;
    public final BrownianModelCalculator message;
    public final List<BrownianModelCalculator> childrenMessages; // used to compute delta statistics
    public final double descendentObservationLogLikelihood; // product over all the leaves under the node of the conditionals of data given imputed preterminals
    public final double descendentVarianceDensity;
    public final List<Node> childrenNodes;
    public final double variance;
    private Particle(
        BrownianModelCalculator message, 
        double variance, 
        List<BrownianModelCalculator> childrenMessages, 
        Node node, List<Node> childrenNodes, 
        double descendentObservationLogLikelihood,
        double descendentVarianceDensity)
    {
      this.message = message;
      this.variance = variance;
      this.childrenMessages = childrenMessages;
      this.node = node;
      this.childrenNodes = childrenNodes;
      this.descendentObservationLogLikelihood = descendentObservationLogLikelihood;
      this.descendentVarianceDensity = descendentVarianceDensity;
    }
    public double logDensity()
    {
      return descendentObservationLogLikelihood + message.logLikelihood() + descendentVarianceDensity;
    }
    @SuppressWarnings("unchecked")
    public Particle(BrownianModelCalculator leaf, Node node, double descendentObservationLogLikelihood)
    {
      this(leaf, Double.NaN, Collections.EMPTY_LIST, node, Collections.EMPTY_LIST, descendentObservationLogLikelihood, 0.0);
    }
    public double sampleValue(Random rand)
    {
      return Normal.generate(rand, message.message[0], message.messageVariance);
    }
    public boolean isLeaf()
    {
      return childrenNodes.isEmpty();
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
  
  /**
   * 
   * @param rand
   * @param maintainFullJoint Should we keep nodes under the fringe? Memory intensive but required to initialize the GIBBS sampler
   * @return
   */
  public StandardParticleApproximation standardSMC_sample(Random rand, boolean maintainFullJoint)
  {
    if (!options.useBetaProposal || varianceRatio(11.0) != 0.0) // 11 is an arbitrary constant
      throw new RuntimeException(); // some assumptions made for simplicity
    
    // list nodes in postorder
    List<Node> traversalOrder = dataset.postOrder();
    
    StandardParticleApproximation approximation = new StandardParticleApproximation(options.nParticles, true);
    double logNorm = 0.0, logZ = 0.0;
    for (Node node : traversalOrder)
    {
      double maxLogLikelihood = Double.NEGATIVE_INFINITY;
      Set<Node> children = dataset.getChildren(node);
      if  (children.isEmpty())
      {
        Particle[] particles = _leafParticleApproximation(rand, node).particles;
        for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
          approximation.particles.get(particleIndex).put(node, particles[particleIndex]);
        
      }
      else
      {
        for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
        {
          double variance = sampleVariance(rand);
          List<BrownianModelCalculator> childrenCalculators = Lists.newArrayList();
          double logWeightUpdate = 0.0;
          List<Node> childrenNodes = Lists.newArrayList();
          double descLogLikelihoods = 0.0, descVar = varianceLogPrior(variance);
          for (Node child : children)
          {
            Particle childParticle = approximation.particles.get(particleIndex).get(child);
            BrownianModelCalculator message = childParticle.message;
            childrenCalculators.add(message);
            childrenNodes.add(child);
            logWeightUpdate = logWeightUpdate - message.logLikelihood();
            descLogLikelihoods += childParticle.descendentObservationLogLikelihood;
            descVar += childParticle.descendentVarianceDensity;
            if (!maintainFullJoint)
              approximation.particles.get(particleIndex).remove(child);
          }
          BrownianModelCalculator combined = BrownianModelCalculator.combine(childrenCalculators, variance);
          double combinedLogLikelihood = combined.logLikelihood();
          logWeightUpdate += combinedLogLikelihood;
          approximation.weights[particleIndex] *= Math.exp(logWeightUpdate);
          
          Particle newParticle = new Particle(combined, variance, childrenCalculators, node, childrenNodes, descLogLikelihoods, descVar);
          approximation.particles.get(particleIndex).put(node, newParticle);
          
          if (combinedLogLikelihood + newParticle.descendentObservationLogLikelihood > maxLogLikelihood)
            maxLogLikelihood = combinedLogLikelihood + newParticle.descendentObservationLogLikelihood;
        }
        double norm = Multinomial.normalize(approximation.weights);
        logNorm += Math.log(norm);
      }
      
      double ess = SMCUtils.ess(approximation.weights);
      double relativeEss = ess/nParticles;
      
      output.printWrite("ess", "level", node.level(), "nodeLabel", node.toString(), "ess", ess, "relativeEss", relativeEss);
      
      if (node.level() == 0)
        output.printWrite("maxLL", "maxLL", maxLogLikelihood);
      
      // Note: make sure to deeply duplicate particles when doing resampling
      if (relativeEss < options.essThreshold + NumericalUtils.THRESHOLD 
          || node.level() < options.levelCutOffForOutput) // the stat printing stuff assumes uniformly weighted particles
      {
        logZ += logNorm;
        logNorm = 0.0;
        
        StandardParticleApproximation newApprox = new StandardParticleApproximation(options.nParticles, false);
        Counter<Integer> resampledCounts = SMCUtils.multinomialSampling(rand, approximation.weights, nParticles);
        
        for (int oldPopulationIndex : resampledCounts.keySet())
          for (int multiplicity = 0; multiplicity < resampledCounts.getCount(oldPopulationIndex); multiplicity++)
          {
            Map<Node, Particle> copy = new LinkedHashMap<Node,Particle>(approximation.particles.get(oldPopulationIndex));
            newApprox.particles.add(copy);
          }
        for (int i = 0; i < options.nParticles; i++)
          newApprox.weights[i] = 1.0/((double)options.nParticles);
        newApprox.resampled = true;
        approximation = newApprox;
      }
      
      if (node.level() < options.levelCutOffForOutput)
      {
        Particle [] particles = convert(approximation, node);
        printNodeStatistics(rand, node, particles);
        
        // report statistics on deltas
        if (node.level() + 1 < options.levelCutOffForOutput && !children.isEmpty())
          printDeltaNodeStatistics(rand, node, particles);
      }
    }
    
    output.printWrite("logZ", "estimate", logZ);
    output.flush();
    return approximation;
  }
  
  private Particle[] convert(StandardParticleApproximation approximation, Node node)
  {
    if (!approximation.resampled)
      throw new RuntimeException();
    
    Particle[] result = new Particle[nParticles];
    
    for (int i = 0; i < nParticles; i++)
      result[i] = approximation.particles.get(i).get(node);
    
    return result;
  }

  private ParticleApproximation recurse(Random rand, Node node)
  {
    Set<Node> children = dataset.getChildren(node);
    

    double logZ = 0.0;
    ParticleApproximation result;
    double maxLogLikelihood = Double.NEGATIVE_INFINITY;
    if (children.isEmpty())
      result = _leafParticleApproximation(rand, node);
    else
    {
      List<ParticleApproximation> childrenApproximations = Lists.newArrayList();
      
      for (Node child : children)
        childrenApproximations.add(recurse(rand, child)); 
      
      result = new ParticleApproximation(nParticles);
      
      for (ParticleApproximation childApprox : childrenApproximations)
        logZ += childApprox.logZEstimate;
       
      for (int particleIndex = 0; particleIndex < nParticles; particleIndex++)
      {
        // sample a variance
        double variance = sampleVariance(rand);
        
        // build product sample from children
        List<BrownianModelCalculator> sampledCalculators = Lists.newArrayList();
        List<Node> childrenNode = Lists.newArrayList();
        double descParticleObsLogl = 0.0;
        double descVar = varianceLogPrior(variance);
        for (ParticleApproximation childApprox : childrenApproximations)
        {
          Particle childParticle = childApprox.particles[particleIndex];
          sampledCalculators.add(childParticle.message);
          // remove the thing below b/c we are assuming resampling at each generation!
//          weight += childApprox.probabilities[particleIndex];
          childrenNode.add(childApprox.particles[particleIndex].node);
          descParticleObsLogl += childParticle.descendentObservationLogLikelihood;
          descVar += childParticle.descendentVarianceDensity;
        }
        
        // compute weight
        BrownianModelCalculator combined = BrownianModelCalculator.combine(sampledCalculators, variance);
        double combinedLogLikelihood = combined.logLikelihood();
        double logWeight = combinedLogLikelihood;
        
        for (BrownianModelCalculator childCalculator : sampledCalculators)
          logWeight = logWeight - childCalculator.logLikelihood();
        logWeight = logWeight + varianceRatio(variance);
        
        // add both qts to result
        Particle newParticle = new Particle(combined, variance, sampledCalculators, node, childrenNode, descParticleObsLogl, descVar);
        result.particles[particleIndex] = newParticle;
        result.probabilities[particleIndex] = logWeight;
        
        if (combinedLogLikelihood + newParticle.descendentObservationLogLikelihood  > maxLogLikelihood)
          maxLogLikelihood = combinedLogLikelihood + newParticle.descendentObservationLogLikelihood;
      }
    }
    
    // exp normalize
    double logNorm = Multinomial.expNormalize(result.probabilities);
    
    logZ += logNorm - Math.log(nParticles);
    
    // monitor ESS
    double ess = SMCUtils.ess(result.probabilities);
    double relativeEss = ess/nParticles;
    output.printWrite("ess", "level", node.level(), "nodeLabel", node.toString(), "ess", ess, "relativeEss", relativeEss);
    
    if (node.level() == 0)
      output.printWrite("maxLL", "maxLL", maxLogLikelihood);
    
    output.flush();
    
    // perform resampling
    result = resample(rand, result, nParticles);
    result.logZEstimate = logZ;
    
    // report statistics on node mean, var
    if (node.level() < options.levelCutOffForOutput)
      printNodeStatistics(rand, node, result.particles);
    
    // report statistics on deltas
    if (node.level() + 1 < options.levelCutOffForOutput && !children.isEmpty())
      printDeltaNodeStatistics(rand, node, result.particles);
    
    return result;
  }
  
  private void printDeltaNodeStatistics(Random rand, Node node,
      Particle [] particles)
  {
    Set<Node> children = dataset.getChildren(node);
    int nChildren = children.size();
    
    double [][] deltaSamples = new double[nChildren][options.nParticles];
    DescriptiveStatistics [] stats = new DescriptiveStatistics[nChildren];
    for (int c = 0; c < nChildren; c++)
      stats[c] = new DescriptiveStatistics();
    
    for (int i = 0; i < options.nParticles; i++)
    {
      Particle p = particles[i % nParticles];
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
      String pathStr = particles[0].childrenNodes.get(c).toString();
      logSamples(output, deltaSamples[c], pathStr, "delta");
      PlotHistogram.from(deltaSamples[c])
        .toPDF(new File(plotsFolder, pathStr + "_delta.pdf"));
      output.printWrite("deltaStats", "path", pathStr, "deltaMean", stats[c].getMean(), "deltaSD", stats[c].getStandardDeviation());
    }
    output.flush();
  }

  private void printNodeStatistics(Random rand, Node node, Particle [] particles)
  {
    Set<Node> children = dataset.getChildren(node);
    
    DescriptiveStatistics meanStats = new DescriptiveStatistics();
    double [] meanSamples = new double[options.nParticles];
    double [] naturalSamples = new double[options.nParticles];
    double [] varianceSamples = children.isEmpty() ? null : new double[options.nParticles];
    DescriptiveStatistics varStats = children.isEmpty() ? null : new DescriptiveStatistics();
    for (int i = 0; i < options.nParticles; i++)
    {
      Particle p = particles[i % nParticles];
      double naturalPoint = p.sampleValue(rand);
      double meanPoint = inverseTransform(naturalPoint);
      meanStats.addValue(meanPoint);
      naturalSamples[i] = naturalPoint;
      meanSamples[i] = meanPoint;
      if (varianceSamples != null)
      {
        varianceSamples[i] = p.variance;
        varStats.addValue(p.variance);
      }
    }
    File plotsFolder = Results.getFolderInResultFolder("histograms");
    String pathStr = node.toString();
    PlotHistogram.from(naturalSamples).toPDF(new File(plotsFolder, pathStr + "_naturalParam.pdf"));
    logSamples(output, naturalSamples, node.toString(), "naturalParam");
    PlotHistogram.from(meanSamples).toPDF(new File(plotsFolder, pathStr + "_logisticMean.pdf"));
    logSamples(output, meanSamples, node.toString(), "meanSample");
    output.printWrite("meanStats", "path", pathStr, "meanMean", meanStats.getMean(), "meanSD", meanStats.getStandardDeviation());
    if (varianceSamples != null)
    {
      PlotHistogram.from(varianceSamples).toPDF(new File(plotsFolder, pathStr + "_var.pdf"));
      logSamples(output, varianceSamples, node.toString(), "varSample");
      output.printWrite("varStats", "path", pathStr, "varMean", varStats.getMean(), "varSD", varStats.getStandardDeviation());
    }
    output.flush();    
  }
  
  public static void logSamples(OutputManager output, double [] samples, String node, String variable)
  {
    for (int i = 0; i < samples.length; i++)
      logSamples(output, samples[i], node, variable, i);
  }
  
  public static void logSamples(OutputManager output, double sample, String node, String variable, int iteration)
  {
    output.write("samples", "node", node.toString(), "variable", variable, "iteration", iteration, "value", sample);
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
  
  private double varianceLogPrior(double variance)
  {
    return modelOptions.useUniformVariance ? 
        MultiLevelBMTreeFactor.uniformLogDensity(variance, 0.0, modelOptions.maxVarianceIfUniform) :
        Exponential.logDensity(variance, modelOptions.variancePriorRateIfExponential);
  }

  private double sampleVariance(Random rand)
  {
    return modelOptions.useUniformVariance ?
        Uniform.generate(rand, 0.0, modelOptions.maxVarianceIfUniform) :
        Exponential.generate(rand, modelOptions.variancePriorRateIfExponential);
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
      
      result.particles[particleIndex] = new Particle(leaf, node, logPi);
      result.probabilities[particleIndex] = logWeight;
    }
    
    // TODO: note expnorm needs to be done by the caller, could lead to problem later
    return result;
  }
  
  public static double logBinomialPr(int nTrials, int nSuccesses, double prOfSuccess)
  {
    if (nTrials < 0 || nSuccesses < 0 || nSuccesses > nTrials || prOfSuccess < 0 || prOfSuccess > 1)
      throw new RuntimeException();
    return SpecialFunctions.logBinomial(nTrials, nSuccesses) 
      + nSuccesses             * Math.log(prOfSuccess) 
      + (nTrials - nSuccesses) * Math.log(1.0 - prOfSuccess);
  }

  private double transform(double numberOnSimplex)
  {
    if (modelOptions.useTransform )
      return SpecialFunctions.logit(numberOnSimplex);
    else
      return numberOnSimplex;
  }
  
  private double inverseTransform(double realNumber)
  {
    if (modelOptions.useTransform)
      return SpecialFunctions.logistic(realNumber);
    else
      return realNumber;
  }
}
