package logistree;

import hmc.AHMC;
import hmc.HMC;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lregression.FeatureExtractor;
import lregression.HomogeneousBaseMeasures;
import lregression.LabeledInstance;
import lregression.MaxentClassifier;
import lregression.MaxentClassifier.ObjectiveFunction;


import org.jblas.DoubleMatrix;

import utils.MultiVariateObj;
import utils.Objective;
import bayonet.coda.CodaParser;
import bayonet.coda.SimpleCodaPlots;
import bayonet.distributions.Multinomial;
import bayonet.math.NumericalUtils;
import bayonet.opt.DifferentiableFunction;
import briefj.OutputManager;
import briefj.collections.Counter;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;



public class LogisticRegressionTest implements Runnable
{
  @Option public int N = 1000;
  @Option public int T = 1000;
  @Option public int D = 100;
  @Option public  int K = 1000;
  @Option public Random rand = new Random(10);
  @Option public double inputSD = 1;
  @Option public double genParamSD = 1;
  @Option public double missingDataPr = 0.9;
  
  @Option public double regularivationVariance = 1.0;
  
  @Option public int thinning = 10;
  @Option public int nFullHMCIterations = 100;
  
  @Option public int rejuvAdaptIters = 100;
  @Option public int rejuvHMCIters = 5;
  
  public static final FeatureExtractor<LabeledInstance<double[], Boolean>, String> extractor = new FeatureExtractor<LabeledInstance<double[], Boolean>, String>()
  {
    private static final long serialVersionUID = 1L;

    @Override
    public Counter<String> extractFeatures(
        LabeledInstance<double[], Boolean> instance)
    {
      Counter<String> result= new Counter<String>();
      
      boolean output = instance.getLabel();
      if (output)
        return result;
      double [] input = instance.getInput();
      for (int d = 0; d < input.length; d++)
        result.setCount(dimFeatName(d), input[d]);
      result.setCount(interceptFeatName, 1.0);
      
      return result;
    }

    @Override
    public double regularizationFactor(String feature)
    {
      return 1.0;
    }
    
  };
  
  public static String dimFeatName(int d) { return "dim-" + d; }
  public static final String interceptFeatName = "intercept";
  
  public static final Collection<Boolean> labels = Arrays.asList(true, false);
  public static final HomogeneousBaseMeasures<double[], Boolean> baseMeasures = new HomogeneousBaseMeasures<double[], Boolean>(labels);
  
  private boolean generated = false;
  public void generateData()
  {
    if (generated) return;
    generated = true;
    
    // generate true weights counter
    trueWeights = new Counter<String>();
    for (int d = 0; d < D; d++)
      trueWeights.setCount(dimFeatName(d), genParamSD * rand.nextGaussian());
    trueWeights.setCount(interceptFeatName, 0.0);
    
    trueClassif = MaxentClassifier.createMaxentClassifierFromWeights(baseMeasures, trueWeights, extractor);
    trainingData = Lists.newArrayList();
    testingData = Lists.newArrayList();
    
    List<Boolean> labels = Lists.newArrayList(trueClassif.getLabels(null));
    
    for (int n = 0; n < N + T; n++)
    {
      // generate some input array
      double [] x = nextLabeledInstance();
      double [] logPrs = trueClassif.logProb(x);
      Multinomial.expNormalize(logPrs);
      int index = Multinomial.sampleMultinomial(rand, logPrs);
      Boolean label = labels.get(index);
      LabeledInstance<double[], Boolean> datum = new LabeledInstance<double[], Boolean>(label, x);
      (n < N ? trainingData : testingData).add(datum);
    }
  }
  public Counter<String> trueWeights = null;
  public List<LabeledInstance<double[], Boolean>> trainingData, testingData;
  public MaxentClassifier<double[], Boolean, String> trueClassif;
  
  
  
  
  public double[] nextLabeledInstance()
  {
    double [] x = new double[D];
    for (int d = 0; d < D; d++)
      if (rand.nextDouble() > missingDataPr)
        x[d] = inputSD * rand.nextGaussian();
    return x;
  }
  
  public static Counter<LabeledInstance<double[], Boolean>> convert(List<LabeledInstance<double[], Boolean>> instances)
  {
    Counter<LabeledInstance<double[], Boolean>> result = new Counter<LabeledInstance<double[], Boolean>>();
    for (LabeledInstance<double[], Boolean> item : instances)
      result.incrementCount(item, 1.0);
    return result;
  }
  
  public MaxentClassifier<double[], Boolean, String> maxentOnFullData; 
  public MaxentClassifier<double[], Boolean, String> maxentOnFullData()
  {
    if (maxentOnFullData != null)
      return maxentOnFullData;
    generateData();
    return maxentOnFullData = MaxentClassifier.learnMaxentClassifier(baseMeasures, convert(trainingData), extractor);
  }
  
  
  public void test(MaxentClassifier<double[], Boolean, String> tested)
  {
    double numOk = 0.0;
    double num = 0.0;
    for (LabeledInstance<double[], Boolean> testPt : testingData)
    {
      num++;
      Boolean decision = tested.probabilitiesCounter(testPt.getInput()).argMax();
      if (decision.equals(testPt.getLabel()))
        numOk++;
    }
    System.out.println("testScore = " + (numOk/num));
  }
  
  public static class ObjectiveAdaptor implements MultiVariateObj, Objective
  {
    public final DifferentiableFunction adapted;
    
    private ObjectiveAdaptor(DifferentiableFunction adapted)
    {
      this.adapted = adapted;
    }

    /**
     * Used by AHMC
     */
    @Override
    public double functionValue(DoubleMatrix vec)
    {
      return adapted.valueAt(vec.data);
    }

    /**
     * Used by AHMC
     */
    @Override
    public DoubleMatrix mFunctionValue(DoubleMatrix vec)
    {
      return new DoubleMatrix(adapted.dimension(),1, adapted.derivativeAt(vec.data));
    }
  }
  
  public class ParticleApproximation
  {
    public double [] logWeights;
    public double [][] atoms;

    public ParticleApproximation()
    {
      this.logWeights = new double[K];
      this.atoms = new double[K][nFeats()];
    }
  }
  
  public int nFeats() { return D+1; }
  
  public ObjectiveFunction subsampledObjectiveFunction(int minIncl, int maxExcl, double regularivationVariance)
  {
    MaxentClassifier<double[], Boolean, String> auxiliary =  maxentOnFullData();
    return auxiliary.objectiveFunction(convert(trainingData.subList(minIncl, maxExcl)), regularivationVariance);
  }
  
  public class LikelihoodCalculator
  {
    private final ObjectiveFunction auxiliaryObjective;
    
    public LikelihoodCalculator(int minIncl, int maxExcl)
    {
      // we set positive infinity to the variance to get rid of the reg. term
      this.auxiliaryObjective = subsampledObjectiveFunction(minIncl, maxExcl, Double.POSITIVE_INFINITY);
    }
    public double logLikelihood(double [] parameters)
    {
      return - auxiliaryObjective.valueAt(parameters);
    }
  }
  
  private Map<Integer,Integer > 
    Ls = Maps.newHashMap();
  private Map<Integer, Double>
    epsilons = Maps.newHashMap();
  
  
  public ParticleApproximation dcSMC(int minIncl, int maxExcl, Random rand, int level)
  {
    ParticleApproximation result = new ParticleApproximation();
    int len = maxExcl - minIncl;
    
    // currently, we just initialize everything at zero, since rejuvenation steps can create particle diversity anyways
    // TODO: improve that? should be cheap ideally.
    if (len == 1)
      return result; 
    
    int middle = minIncl + len / 2;
    
    ParticleApproximation 
      left = dcSMC(minIncl, middle, rand, level + 1),
      right= dcSMC(middle, maxExcl, rand, level + 1);
    
    LikelihoodCalculator 
      leftDataSplitLikelihood = new LikelihoodCalculator(minIncl, middle),
      rightDataSplitLikelihood= new LikelihoodCalculator(middle, maxExcl);
    
    for (int k = 0; k < K; k++)
    {
      double [] 
        leftParam  =  left.atoms[k],
        rightParam = right.atoms[k];
      
      double 
        logLR = rightDataSplitLikelihood.logLikelihood(leftParam)  - rightDataSplitLikelihood.logLikelihood(rightParam),
        logRL = leftDataSplitLikelihood .logLikelihood(rightParam) - leftDataSplitLikelihood .logLikelihood(leftParam);
      
      double [] samplingArray = new double[]{ logLR, logRL };
      Multinomial.expNormalize(samplingArray);
      int sampled = Multinomial.sampleMultinomial(rand, samplingArray);
      
      result.atoms[k] = sampled == 0 ? left.atoms[k] : right.atoms[k];
      result.logWeights[k] = NumericalUtils.logAdd(logLR, logRL);
    }
    
    // TODO: update Z estimate
    
    // print ESS
    
    // currently: always resample, do adaptive resampling schemes later if needed
    boolean resamplingNeeded = true;
    if (resamplingNeeded)
    {
      Multinomial.expNormalize(result.logWeights);
      System.out.println("ess = " + ess(result.logWeights) + ", level = " + level + ", interval = " + minIncl + ", " + maxExcl);
      
      // determine the indices (in old array) that get resampled, and the corresponding multiplicity
      Counter<Integer> resampledCounts = multinomialSampling(rand, result.logWeights, K);
      // use the indices to create a new atoms array
      double [][] newAtoms = new double[K][nFeats()];
      int currentIndex = 0;
      for (int resampledIndex : resampledCounts)
        for (int i = 0; i < resampledCounts.getCount(resampledIndex); i++)
          newAtoms[currentIndex++] = result.atoms[resampledIndex];
      result.atoms = newAtoms;
      
      // reset particle logWeights
      double log1overK = -Math.log(K);
      for (int k = 0; k < K; k++)
        result.logWeights[k] = log1overK;
      
      // prepare for rejuvenation
      ObjectiveAdaptor adaptor = new ObjectiveAdaptor(subsampledObjectiveFunction(minIncl, maxExcl, regularivationVariance)); 
      
      // do some AHMC adaptation on one of the particles to find epsilon, L for efficient HMCs
      if (!Ls.containsKey(level))
      {
        AHMC ahmc = AHMC.initializeAHMCWithLBFGS(rejuvAdaptIters, rejuvAdaptIters, adaptor, adaptor, nFeats());
        ahmc.sample(rand);
        Ls.put(level, ahmc.getL());
        epsilons.put(level, ahmc.getEpsilon());
      }
      int L = Ls.get(level);
      double epsilon = epsilons.get(level);
      
      // do some HMC rejuvenation steps on each particle
      for (int k = 0; k < K; k++)
      {
        DoubleMatrix current = new DoubleMatrix(result.atoms[k]);
        
        for (int i = 0; i < rejuvHMCIters; i++)
          current = HMC.doIter(rand, L, epsilon, current, adaptor, adaptor).next_q;
        
        result.atoms[k] = current.data.clone();
      }
    }
     
    return result;
  }
  
  public static Counter<Integer> multinomialSampling(Random rand, double [] w, int nSamples)
  {
    List<Double> darts = new ArrayList<Double>(nSamples);
    for (int n = 0; n < nSamples; n++)
      darts.add(rand.nextDouble());
    Collections.sort(darts);
    Counter<Integer> result = new Counter<Integer>();
    double sum = 0.0;
    int nxtDartIdx = 0;
    for (int i = 0; i < w.length; i++)
    {
      final double curLen = w[i];
      if (curLen < 0 - NumericalUtils.THRESHOLD)
        throw new RuntimeException();
      final double right = sum + curLen;
      
      for (int dartIdx = nxtDartIdx; dartIdx < darts.size(); dartIdx++)
        if (darts.get(dartIdx) < right)
        {
          result.incrementCount(i, 1.0);
          nxtDartIdx++;
        }
        else 
          break;
      sum = right;
    }
    if (Double.isNaN(sum))
      throw new RuntimeException();
    NumericalUtils.checkIsClose(1.0, sum);
    if (result.totalCount() != nSamples)
      throw new RuntimeException();
    return result;
  }
  
  public static double ess(double [] ws)
  {
    NumericalUtils.checkIsClose(1.0, Multinomial.getNormalization(ws));
    double sumOfSqr = 0.0;
    for (double w : ws) sumOfSqr+=w*w;
    return 1.0/sumOfSqr;
  }
  
  public void hmcOnFullData(MaxentClassifier<double[], Boolean, String> maxent)
  {
    File fullCSVFolder = Results.getFolderInResultFolder("full-hmc");
    OutputManager output = new OutputManager();
    output.setOutputFolder(fullCSVFolder);
    ObjectiveAdaptor adaptor = new ObjectiveAdaptor(maxent.objectiveFunction(convert(trainingData), 1.0));
    AHMC ahmc = AHMC.initializeAHMCWithLBFGS(250, 250, adaptor, adaptor, nFeats());
    DoubleMatrix current = ahmc.sample(rand);
    
    for (int i = 0; i < nFullHMCIterations; i++)
    {
      
      current = HMC.doIter(rand, ahmc.getL(), ahmc.getEpsilon(), current, adaptor, adaptor).next_q;
      
      if (i % thinning == 0)
      {
        System.out.println("iter " + i);
        output.write(interceptFeatName, "iteration", i, "value", current.get(maxent.getFeatureIndex(interceptFeatName)));
        for (int d = 0; d < D; d++)
          output.write(dimFeatName(d), "iteration", i, "value", current.get(maxent.getFeatureIndex(dimFeatName(d))));
      }
    }
    output.close();
    
    File codaContents = Results.getFileInResultFolder("samples-full-hmc.coda");
    File codaIndex = Results.getFileInResultFolder("samples-index-full-hmc.coda");
    
    CodaParser.CSVToCoda(codaIndex, codaContents, fullCSVFolder);
    File outputPDF = Results.getFileInResultFolder("coda-plots-full-hmc.pdf");
    SimpleCodaPlots plotter = new SimpleCodaPlots(codaContents, codaIndex);
    plotter.toPDF(outputPDF);
  }
  
  public void run()
  {
    generateData();
    System.out.println(trueWeights);
    MaxentClassifier<double[], Boolean, String> maxent = maxentOnFullData();
    System.out.println(maxent.weights());
    System.out.println("Learned:");
    test(maxent);
    System.out.println("With true weights:");
    test(trueClassif);
    hmcOnFullData(maxent);
    System.out.println("Starting recursive method");
    dcSMC(0, D, rand, 0);
  }
      
  public static void main(String [] args)
  {
    Mains.instrumentedRun(args, new LogisticRegressionTest());
  }
}
