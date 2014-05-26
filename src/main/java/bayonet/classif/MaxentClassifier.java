package bayonet.classif;


import java.io.Serializable;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;


import org.apache.commons.lang3.tuple.Pair;

import bayonet.distributions.Multinomial;
import bayonet.distributions.Normal;
import bayonet.math.NumericalUtils;
import bayonet.math.SparseVector;
import bayonet.opt.DifferentiableFunction;
import bayonet.opt.GradientMinimizer;
import bayonet.opt.LBFGSMinimizer;
import briefj.collections.Counter;
import briefj.opt.Option;




/**
 * A multiclass logistic regression model
 * 
 * Regularization options: L1 and L2
 * 
 * Training: 
 *  -maximum likelihood training using the LBFGS and Orthan-Wise/LBFGS algorithms
 *  -sampling from posterior using MH or the Hamiltonian Hybrid MCMC algorithms
 * 
 * 
 * @author Alexandre Bouchard
 *
 * @param <I> Type of input
 * @param <L> Type of label (output or prediction)
 * @param <F> Type of features extracted from LabeledInstance<I,L>f
 */
public final class MaxentClassifier<I,L,F> implements Serializable
{
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  //
  // Fields
  //
  private final FeatureVectorsInterface<I, L> featureVectors;
  private final BaseMeasures<I, L> baseMeasures;
  private double [] weights;
  private final double [] regularizationCenters;
  // 
  // Creation
  //
  /**
   * Still need to learn the model after calling this constructor, so it's 
   * private.
   * Note that retraining requires a fresh MaxentClassifier object because the
   * dimensionality of the feature vectors depend on the training data (via
   * the number of observed active features)
   */
  private MaxentClassifier(
      final BaseMeasures<I, L> baseMeasures,
      final Set<LabeledInstance<I, L>> data, 
      final FeatureExtractor<LabeledInstance<I,L>, F> extractor,
      final Counter<F> regularizerCenters, boolean useHash)
  {
    // precompute some quantities for efficiency
    this.baseMeasures = baseMeasures;
    this.featureVectors = useHash ? HashedFeatureVectors.createFeatureVectors(extractor) :
        FeatureVectors.createFeatureVectors(baseMeasures, data, extractor);
    this.regularizationCenters = featureVectors.createInitialWeight(regularizerCenters);
  }
  private MaxentClassifier(BaseMeasures<I, L> baseMeasures,
      Counter<F> weights, FeatureExtractor<LabeledInstance<I, L>, F> extractor, boolean useHash)
  {
    this.baseMeasures = baseMeasures;
    this.featureVectors = useHash ? HashedFeatureVectors.createFeatureVectors(extractor) :
      FeatureVectors.createFeatureVectorsFromSet(weights.keySet(), extractor);
    this.weights = featureVectors.createInitialWeight(weights);
    this.regularizationCenters = featureVectors.createInitialWeight();
  }
  /**
   * Create a maxent classifier and train it using LBFGS 
   * @param trainingData
   * @param extractor
   * @param learningOptions
   * @return
   */
  public static <I,L,F> MaxentClassifier<I,L,F> learnMaxentClassifier(
      BaseMeasures<I,L> baseMeasures,
      Counter<LabeledInstance<I, L>> trainingData,
      FeatureExtractor<LabeledInstance<I,L>, F> extractor,
      MaxentOptions<F> learningOptions)
  {
    return learnMaxentClassifier(
        baseMeasures,
        trainingData,
        extractor,
        learningOptions,
        new Counter());
  }
  public static <I,L,F> MaxentClassifier<I,L,F> learnMaxentClassifier(
      BaseMeasures<I,L> baseMeasures,
      Counter<LabeledInstance<I, L>> trainingData,
      FeatureExtractor<LabeledInstance<I,L>, F> extractor,
      MaxentOptions<F> learningOptions,
      Counter<F> regularizerCenters)
  {
    MaxentClassifier<I, L, F> result = new MaxentClassifier<I, L, F>(
        baseMeasures, trainingData.keySet(), extractor, regularizerCenters, learningOptions.useHash);
    result.learn(trainingData, learningOptions);
    return result;
  }
  public static <I,L,F> MaxentClassifier<I,L,F> learnMaxentClassifier(
      BaseMeasures<I,L> baseMeasures,
      Counter<LabeledInstance<I, L>> trainingData,
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    return learnMaxentClassifier(baseMeasures, trainingData, extractor, new MaxentOptions<F>(), new Counter<F>());
  }
  public static <I,L,F> MaxentClassifier<I,L,F> createMaxentClassifierFromHashWeights(
      BaseMeasures<I,L> baseMeasures,
      Counter<F> weights,
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    return new MaxentClassifier<I,L,F>(baseMeasures,weights,extractor, true);
  }
  public static <I,L,F> MaxentClassifier<I,L,F> createMaxentClassifierFromWeights(
      BaseMeasures<I,L> baseMeasures,
      Counter<F> weights,
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    return new MaxentClassifier<I,L,F>(baseMeasures,weights,extractor, false);
  }
//  public static <I,L,F> MaxentClassifier<I,L,F> sampleMaxentClassifier(
//      BaseMeasures<I,L> baseMeasures,
//      Counter<LabeledInstance<I, L>> trainingData,
//      FeatureExtractor<LabeledInstance<I,L>, F> extractor,
//      MaxentOptions<F> options)
//  {
//    MaxentClassifier<I, L, F> result = new MaxentClassifier<I, L, F>(
//        baseMeasures, trainingData.keySet(), extractor);
//    result.sample(trainingData, options);
//    return result;
//  }
  //
  // public methods
  //
  public double [] logProb(I input)
  {
    return logProb(input, weights, false); 
  }
  public Counter<L> probabilitiesCounter(I input)
  {
    Counter<L> result = new Counter<L>();
    double [] prs = logProb(input);
    Multinomial.expNormalize(prs);
    int i = 0;
    for (L label : getLabels(input))
      result.setCount(label, prs[i++]);
    return result; 
  }
  public double [] logProb(I input, boolean cache)
  {
    return logProb(input,weights,cache);
  }
  public double localLogNormalization(I input)
  {
    return localLogNormalization(input, false);
  }
  public double localLogNormalization(I input, boolean cache)
  {
    return NumericalUtils.logAdd(unNormLogProb(input, weights, cache));
  }
  /**
   * The possible labels corresp. to the given input
   * @return
   */
  public SortedSet<L> getLabels(I input)
  {
    return baseMeasures.support(input);
  }
  @Override
  public String toString()
  {
    Counter namedCounter = featureVectors.namedCounter(weights);
    StringBuilder builder = new StringBuilder();
    for (Object key : namedCounter)
    {
      builder.append(key.toString() + "\t" + namedCounter.getCount(key) + "\n");
    }
    return builder.toString();
  }
  public int numberOfActiveFeatures()
  {
    return featureVectors.dim();
  }
//  public int numberOfTrainingTerms()
//  {
//    return trainSize; //featureVectors.size();
//  }
  public Counter weights()
  {
    return featureVectors.namedCounter(weights);
  }
  public double [] rawWeights() { return weights; }
  //
  // private methods
  //
  /**
   * Compute the log probability for all the possible labels and the given
   * instance, using the provided weight w and precomputed featureVectors
   * The order of the result mirors that of the field "labels"
   * This ignores the Normal prior structure used for LGFBS learning
   * @param instance
   * @param w
   * @return
   */
//  private double [] logProb(I instance, double [] w, boolean cache)
//  {
//    SortedSet<L> labels = baseMeasures.support(instance);
//    double [] result = new double[labels.size()];
//    // 1 - compute the unnormalized log probs (dot products)
//    int l = 0;
//    for (L label : labels)
//    {
//      SparseVector featureVector = featureVectors.getFeatureVector(instance, label, cache);
//      result[l] = featureVector.dotProduct(w);
//      l++;
//    }
//    // 2 - normalize
//    double norm = SloppyMath.logAdd(result);
//    for (l = 0; l < labels.size(); l++) result[l] = result[l] - norm;
//    return result;
//  }
  
  private double [] logProb(I instance, double [] w, boolean cache)
  {
    double [] result = unNormLogProb(instance, w, cache);
    // 2 - normalize
    double norm = NumericalUtils.logAdd(result);
    for (int l = 0; l < result.length; l++) result[l] = result[l] - norm;
//    if (((HLContext) instance).type == ChoiceType.INS)
//      System.out.println("logProbNorm(" + instance + ")=" + norm);
    return result;
  }
  private double [] unNormLogProb(I instance, double [] w, boolean cache)
  {
    SortedSet<L> labels = baseMeasures.support(instance);
    double [] result = new double[labels.size()];
    // 1 - compute the unnormalized log probs (dot products)
    int l = 0;
    for (L label : labels)
    {
      SparseVector featureVector = featureVectors.getFeatureVector(new LabeledInstance<I, L>(label, instance), cache);
      result[l] = featureVector.dotProduct(w);
      l++;
    }
    return result;
  }
  
  private void logs(Object o){}
  
  // 
  // Learning related
  //
  /**
   * Learn weights
   */
  private void learn(
      Counter<LabeledInstance<I, L>> suffStats, 
      MaxentOptions<F> options)
  {
    double [] init = featureVectors.createInitialWeight(options.initialWeights);
    final ObjectiveFunction objective = objectiveFunction(suffStats, options.sigma, options.useL1);
    logs("featureVectors.dim()=" + featureVectors.dim() );
//        + ",featureVectors.size()=" + trainSizefeatureVectors.size());
    if (options.learningAlgo == LearningAlgorithm.OW)
    {
      throw new RuntimeException();
//      logs("Optimization using OW");
//      OW_LBFGSMinimizer minimizer = new OW_LBFGSMinimizer(options.iterations);
//      this.weights = minimizer.minimize(objective, init, options.tolerance);
    }
    else if (options.learningAlgo == LearningAlgorithm.LBFGS)
    {
      logs("Optimization using LBFGS");
      if (options.useL1) throw new RuntimeException();
      GradientMinimizer minimizer = new LBFGSMinimizer(options.iterations);
      this.weights = minimizer.minimize(objective, init, options.tolerance);
    }
    else if (options.learningAlgo == LearningAlgorithm.MH)
    {
      double [] prev = init, cur = new double[init.length];
      double prevLogPr = -objective.valueAt(prev);
//      LogInfo.track("MH sampling",true);
//      logs("Initial log pr:" + prevLogPr + ", norm:" + NumUtils.l2Norm(prev));
//      SummaryStatistics acceptRatioStats = new SummaryStatistics();
      long initTime = System.currentTimeMillis();
      for (int iter = 0; iter < options.iterations; iter++)
      {
        for (int i = 0; i < cur.length; i++)
          cur[i] = prev[i] + options.proposalStdDev * Normal.generateStandardNormal(options.rand);
        final double curLogPr = -objective.valueAt(cur);
        final double ratio = Math.min(1, Math.exp(curLogPr-prevLogPr));
//        acceptRatioStats.addValue(ratio);
        if (options.rand.nextDouble() < ratio)
        {
          // accept
          for (int i = 0; i < cur.length; i++)
            prev[i] = cur[i];
          prevLogPr = curLogPr;
//          logs("Move " + (iter+1)+"/" + options.iterations + " accepted, new log pr:" + curLogPr + ", norm:" + NumUtils.l2Norm(cur));
        }
//        else
//          logs("Move " + (iter+1)+"/" + options.iterations + " rejected (ratio=" + ratio + ")");
        logs("MH-iteration " + (iter+1) + "/" + options.iterations + " " + 
            (System.currentTimeMillis()-initTime) + " " +
            curLogPr);
        
      }
//      LogInfo.end_track();
//      logs("Mean acceptance ratio:" + acceptRatioStats.getMean());
      this.weights = prev;
    }
    else if (options.learningAlgo == LearningAlgorithm.HMC)
    {
      throw new RuntimeException();
//      HMCMC sampler = new HMCMC(objective, init);
//      sampler.setEpsilon(options.hmcEpsilon);
//      sampler.setTau(options.hmcTau );
//      sampler.setProposalStdDev(options.proposalStdDev);
//      long initTime = System.currentTimeMillis();
//      LogInfo.track("HMC sampling",true);
//      for (int iter = 0; iter < options.iterations; iter++)
//      {
//        sampler.next(options.rand);
//        logs("HMC-iteration " + (iter+1) + "/" + options.iterations + " " + 
//            (System.currentTimeMillis()-initTime) + "ms " +
//            -sampler.getValue());
////        if (((iter+1) % options.regResamplingFreq) == 0)
////        {
////          // resample regularization param
////          // assumes gamma prior with hyper params alpha, beta (in the rate-inverse scale parameterization)
////          final double shapeUpdated = options.regResamplingPriorShape + ((double)objective.dim())/2.0;
////          final double rateUpdated = options.regResamplingPriorRate + NumUtils.l2NormSquared(sampler.getPosition())/2.0;
////          // resample precision
////          final double newPrecision = Gamma.sample(options.rand, shapeUpdated, rateUpdated);
////          final double newVariance = 1.0/newPrecision;
////          objective.setSigma(newVariance);
////          logs("Resampled variance:" + newVariance);
////        }
//      }
//      LogInfo.end_track();
//      this.weights = sampler.getPosition();
//      LogInfo.logs("Mean acceptance ratio:" + sampler.acceptRatio());
    }
    else throw new RuntimeException();
  }
  public ObjectiveFunction objectiveFunction(
      Counter<LabeledInstance<I, L>> suffStats, 
      double sigma, boolean useL1) 
  { 
    return new ObjectiveFunction(suffStats, sigma, useL1);
  }
  public ObjectiveFunction objectiveFunction(
      Counter<LabeledInstance<I, L>> suffStats, 
      double sigma) 
  { 
    return new ObjectiveFunction(suffStats, sigma, false);
  }
  public static enum LearningAlgorithm 
  { 
    OW(false), LBFGS(false), MH(true), HMC(true); 
    public final boolean isSampling;
    private LearningAlgorithm(boolean isSampling) { this.isSampling = isSampling; }
  }
  public static class MaxentOptions<F> implements Cloneable
  {
    @Option public boolean useHash = false;
    
    @Option public double regResamplingPriorShape = 2.0;
    @Option public double regResamplingPriorRate = 2.0;
    @Option public int regResamplingFreq = 50;
    @Option public int hmcTau = 100;
    @Option public double hmcEpsilon = 0.005;
    @Option public Random rand = new Random(1);
    @Option public LearningAlgorithm learningAlgo = LearningAlgorithm.LBFGS;
    @Option public double proposalStdDev = 0.05;
    @Option public boolean useL1 = false;
    @Option(gloss="Default regularization factor") 
    public double sigma = 1.0;
    @Option(gloss="Max number of learning iterations") 
    public int iterations = 100;
    @Option(gloss="Under this delta, stop LBFGS optimization") 
    public double tolerance = 1e-8;
    /**
     * Feature -> corresp. init. weight
     */
    public Counter<F> initialWeights = new Counter<F>();
    public static <F> MaxentOptions<F> cloneWithWeights(MaxentOptions<F> model, Counter<F> initWeights)
    {
      try
      {
        MaxentOptions<F> result = (MaxentOptions) model.clone();
        result.initialWeights = initWeights;
        return result;
      } catch (Exception e) { throw new RuntimeException(e); }
    }
    public MaxentOptions() {}
    @Override public Object clone()throws CloneNotSupportedException{return super.clone();}
  }
  /**
   * Negative Loglikelihood of the data, seen as a function of the parameters,
   * penalized by a L1/2 regularization corresponding to a Laplace/Normal (mu,sigma)
   * prior on the parameters
   * @author Alexandre Bouchard
   * 
   */
  public final class ObjectiveFunction implements /*DifferentiableRegularizableFunction,*/ DifferentiableFunction //, Energy
  {
    /**
     * Cached and precomputed quantities
     */
    private final Counter<LabeledInstance<I, L>> trainingCounts;
    private final SparseVector actualExpectedFeatureVector;
    private final  Counter<I> marginalExpectedCounts;
    private final boolean useL1;
    public ObjectiveFunction(Counter<LabeledInstance<I, L>> expectedCounts, double sigma, boolean useL1)
    {
      this.useL1 = useL1;
      this.sigma = sigma;
      expectedCounts = restrictToValidTrainingData(expectedCounts, baseMeasures);
      // precompute some quantities for efficiency
      this.trainingCounts = expectedCounts;
      this.marginalExpectedCounts = computeMarginalExpectedCounts(trainingCounts);
      this.actualExpectedFeatureVector = computeActualExpectedFeatureVector(featureVectors, trainingCounts);
    }
    /**
     * Smoothing
     */
    private double sigma; //, mu = 0.0;
    public void setSigma(double s) 
    {
      if (s <= 0) throw new RuntimeException();
      lastX = null;
      sigma = s;
    }
    /**
     * More Caching
     */
    double lastValue;
    double[] lastDerivative, lastUnregDeriv;
    double[] lastX = null;
    public int dimension() {
      return featureVectors.dim();
    }
    public double valueAt(double[] x) {
      ensureCache(x);
      return lastValue;
    }
    public double[] derivativeAt(double[] x) {
      ensureCache(x);
      return lastDerivative;
    }
    public double[] unregularizedDerivativeAt(double[] x)
    {
      ensureCache(x);
      return lastUnregDeriv;
    }
    private void ensureCache(double[] x) {
      if (requiresUpdate(lastX, x)) {
        Pair<Double, double[]> currentValueAndDerivative = calculate(x);
        lastValue = currentValueAndDerivative.getLeft(); //getFirst();
        lastDerivative = currentValueAndDerivative.getRight(); //getSecond();
        lastUnregDeriv = lastDerivative.clone();
        lastValue += regularize(x, lastDerivative); // modifies lastDerivative in place
        if (lastX == null)
          lastX = new double[x.length];
        for (int i = 0; i < x.length; i++)
          lastX[i] = x[i];
      }
    }
    private double regularize(double[] x, double[] gradient)
    {
      double result = 0.0;
      // Penalty/prior negative loglikelihood
      for (int j = 0; j < x.length; j++)
      {
        final double diff = x[j] - regularizationCenters[j]; //mu;
        // for the gradient
        final double sigma = this.sigma * featureVectors.getRegularizationFactor(j);
        if (useL1)
        {
          if (diff == 0.0)
          {
                 if (gradient[j] < -1.0/2/sigma) gradient[j] -= +1.0/2/sigma;
            else if (gradient[j] > +1.0/2/sigma) gradient[j] -= -1.0/2/sigma;
            else  
              gradient[j] = 0;
          }
          else
            gradient[j] += (diff < 0 ? -1 : +1) / 2 / sigma;
          
          result += Math.abs(diff) / 2/ sigma;
        }
        else
        {
          gradient[j] += diff/sigma;
          // for the fValue
          result += diff*diff/2.0/sigma;
        }
      }
      return result;
    }
    private boolean requiresUpdate(double[] lastX, double[] x) {
      if (lastX == null) return true;
      for (int i = 0; i < x.length; i++) {
        if (lastX[i] != x[i])
          return true;
      }
      return false;
    }
    /**
     * Actual work is here
     * fValue is proportional to the negative loglikelihood of the data for param x + 
     *  the normal loglikelihood penalty, (normalization of normal distn ignored)
     * i.e. the negative completed expected loglikelihood of the Normal regularized model
     * 
     * the gradient it the array of p.d. of this quantity
     * @param x
     * @return (fValue, gradient)
     */
    private Pair<Double, double[]> calculate(final double[] x)
    {
      double fValue = 0.0;
      final double [] gradient = new double[x.length];
      // leading cached term in the gradient
      actualExpectedFeatureVector.linearIncrement(-1.0, gradient); 
      // the other terms are computed term by term
      for (final I input : marginalExpectedCounts.keySet())
      {
        SortedSet<L> labels = baseMeasures.support(input);
        final double [] logProb = logProb(input, x, true);
        final double currentMarginalCount = marginalExpectedCounts.getCount(input);
        int l = 0;
        for (final L label : labels)
        {
          // increment the function value
          LabeledInstance<I, L> key = new LabeledInstance<I, L>(label, input);
          final double currentCount = trainingCounts.getCount(key);
          fValue = fValue - currentCount * logProb[l];
          // increment the gradient vector
          final SparseVector currentVector = featureVectors.getFeatureVector(key, true);
          final double coef = Math.exp(logProb[l]) * currentMarginalCount;
          currentVector.linearIncrement(coef, gradient);
          l++;
        }
      }
      // note: regularization is done afterwards by regularize()
      return Pair.of(fValue, gradient);
    }
    public int gradientDim()
    {
      return featureVectors.dim();
    }
    public int dim() { return gradientDim(); }
    public double[] gradientAt(double[] x) { return derivativeAt(x); }
  }
  //
  // Precomputation code for efficiency
  //
  private static <I,L,F> SparseVector computeActualExpectedFeatureVector(
      FeatureVectorsInterface<I,L> featureVectors,
      Counter<LabeledInstance<I, L>> trainingCounts)
  {
    double [] result = new double[featureVectors.dim()];
    for (LabeledInstance<I, L> labeledInstance : trainingCounts.keySet())
    {
      double currentCount = trainingCounts.getCount(labeledInstance);
      featureVectors.getFeatureVector(labeledInstance, true).linearIncrement(currentCount, result);
    }
    return new SparseVector(result);
  }
  private static <I,L> Counter<I> computeMarginalExpectedCounts(
      Counter<LabeledInstance<I, L>> trainingCount)
  {
    Counter<I> result = new Counter<I>();
    for (LabeledInstance<I, L> labeledInstance : trainingCount)
    {
      double currentCount = trainingCount.getCount(labeledInstance);
      result.incrementCount(labeledInstance.getInput(), currentCount);
    }
    return result;
  }
  /**
   * Restrict training to data points that are allowed by the model
   */
  private static <I,L> Counter<LabeledInstance<I, L>> restrictToValidTrainingData(Counter<LabeledInstance<I, L>> data,
      BaseMeasures<I,L> baseMeasures)
  {
    Counter<LabeledInstance<I, L>> result = new Counter<LabeledInstance<I, L>>();
    for (LabeledInstance<I, L> trainingDatum : data.keySet())
    {
      L label = trainingDatum.getLabel();
      I input = trainingDatum.getInput();
      if (baseMeasures.support(input).contains(label)) 
      {
        double count = data.getCount(trainingDatum);
        result.setCount(trainingDatum, count);
      }
    }
    return result;
  }
}
