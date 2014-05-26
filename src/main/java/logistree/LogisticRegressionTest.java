package logistree;

import hmc.AHMC;
import hmc.HMC;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.jblas.DoubleMatrix;

import utils.MultiVariateObj;
import utils.Objective;

import com.google.common.collect.Lists;

import bayonet.classif.FeatureExtractor;
import bayonet.classif.HomogeneousBaseMeasures;
import bayonet.classif.LabeledInstance;
import bayonet.classif.MaxentClassifier;
import bayonet.coda.CodaParser;
import bayonet.coda.SimpleCodaPlots;
import bayonet.distributions.Multinomial;
import bayonet.opt.DifferentiableFunction;
import briefj.BriefIO;
import briefj.collections.Counter;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;



public class LogisticRegressionTest implements Runnable
{
  @Option public int N = 10000;
  @Option public int T = 10000;
  @Option public int D = 100;
  @Option public Random rand = new Random(10);
  @Option public double inputSD = 10;
  @Option public double genParamSD = 1;
  @Option public double missingDataPr = 0.9;
//  @Option public double genDeterminize = 0.1;
  
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
  
  public MaxentClassifier<double[], Boolean, String> trainMaxentOnFullData()
  {
    generateData();
    return MaxentClassifier.learnMaxentClassifier(baseMeasures, convert(trainingData), extractor);
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
  
  public void run()
  {
    generateData();
    System.out.println(trueWeights);
    MaxentClassifier<double[], Boolean, String> maxent = trainMaxentOnFullData();
    System.out.println(maxent.weights());
    System.out.println("Learned:");
    test(maxent);
    System.out.println("With true weights:");
    test(trueClassif);
    
    // next step: try AHMC on that
    File outputCSV = Results.getFileInResultFolder("samples.csv");
    PrintWriter outputCSVWriter = BriefIO.output(outputCSV);
    ObjectiveAdaptor adaptor = new ObjectiveAdaptor(maxent.objectiveFunction(convert(trainingData), 1.0));
    AHMC ahmc = AHMC.initializeAHMCWithLBFGS(250, 250, adaptor, adaptor, D+1);
    DoubleMatrix current = ahmc.sample(rand);
    
    for (int i = 0; i < 10000; i++)
    {
      if (i % 100 == 0)
        System.out.println("iter " + i);
      current = HMC.doIter(rand, ahmc.getL(), ahmc.getEpsilon(), current, adaptor, adaptor).next_q;
      outputCSVWriter.println("" + i + "," + current.data[0]); //join(current.data, ","));
    }
    outputCSVWriter.close();
    
    File codaContents = Results.getFileInResultFolder("samples.coda");
    File codaIndex = Results.getFileInResultFolder("samples-index.coda");
    
    CodaParser.CSVToCoda(codaIndex, codaContents, Results.getResultFolder());
    File outputPDF = Results.getFileInResultFolder("coda-plots.pdf");
    SimpleCodaPlots plotter = new SimpleCodaPlots(codaContents, codaIndex);
    plotter.toPDF(outputPDF);
  }
  
//  public String join(double [] data, String sep)
//  {
//    StringBuilder result = new StringBuilder();
//    for (int i = 0; i < data.length; i++)
//      result.append(data[i] + (i == data.length - 1? "" : sep));
//    return result.toString();
//  }
      
  public static void main(String [] args)
  {
    Mains.instrumentedRun(args, new LogisticRegressionTest());
    
    
//    System.out.println(samples);
    
// // - training instances
//    // create datums
//    LabeledInstance<String[], String> datum1 = new LabeledInstance<String[], String>("cat", new String[]{"fuzzy", "claws", "small"});
//    LabeledInstance<String[], String> datum2 = new LabeledInstance<String[], String>("bear", new String[]{"fuzzy", "claws", "big"});
//    LabeledInstance<String[], String> datum3 = new LabeledInstance<String[], String>("cat", new String[]{"claws", "medium"});
//    LabeledInstance<String[], String> datum4 = new LabeledInstance<String[], String>("cat", new String[]{"claws", "small"});
//    Counter<LabeledInstance<String[],String>> training = new Counter<LabeledInstance<String[],String>>();
//    training.incrementCount(datum1, 1.0);
//    training.incrementCount(datum2, 1.0);
//    training.incrementCount(datum3, 1.0);
//    // simple feature extractor
//
//    
//
//    MaxentClassifier<String[], String, String> maxent =
//      MaxentClassifier.learnMaxentClassifier(fbm, training, featureExtractor);
//    System.out.println(maxent.getLabels(datum4.getInput()));
//    System.out.println(Arrays.toString((maxent.logProb(datum4.getInput()))));
  }
}
