package bayonet.classif;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bayonet.math.SparseVector;
import briefj.Indexer;
import briefj.collections.Counter;



/**
 * A cache for feature vectors based on an indexer
 * 
 * Create using the static method createFeatureVectors
 * 
 * After creation, the indexer will not be modified.  Unindexed features will 
 * be ignored afterwards (see getFeatureVector()) (they correspond to zero
 * weights)
 * 
 * @author Alexandre Bouchard
 *
 * @param <I> Input type
 * @param <L> Label type
 * @param <F> Feature type
 */
public class FeatureVectors<I,L,F> implements FeatureVectorsInterface<I,L>
{
  /**
   * instance+label -> index in indexer, value of the feature
   */
  private final Map<LabeledInstance<I,L>,SparseVector> featureVectors =
    new HashMap<LabeledInstance<I,L>, SparseVector>();
  public final Indexer<F> indexer;
  private final FeatureExtractor<LabeledInstance<I,L>, F> extractor;
  private final  int dim;
  private final double [] regularizationFactors;
  //
  //
  //
  /**
   * Create the feature vectors and the indexer associated with it
   * 
   * Feature that are indexed are all (input, label) s.t. (input, label_2) was
   * found in training for some label_2
   */
  public static <I,L,F> FeatureVectorsInterface<I,L> createFeatureVectors(
      BaseMeasures<I,L> baseMeasures,
      Set<LabeledInstance<I, L>> training, 
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    Indexer<F> indexer = computeIndexes(baseMeasures, training, extractor);
    double [] regularizationFactor = computeRegFactors(indexer, extractor);
    FeatureVectors<I, L, F> result = 
      new FeatureVectors<I, L, F>(extractor, indexer, regularizationFactor);
//    result.correctnessField = indexer.hashCode(); // for assertions
    for (LabeledInstance<I, L> labeledInstance : training)
    {
      result.addFeatureVector(labeledInstance);
    }
    return result;
  }
  public static <I,L,F> FeatureVectorsInterface<I,L> createFeatureVectorsFromSet(
      Set<F> features, 
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    Indexer<F> indexer = computeIndexesFromSet(features);
    FeatureVectors<I, L, F> result = 
      new FeatureVectors<I, L, F>(extractor, indexer, null);
    return result;
  }
//  private int correctnessField;
  //
  //
  //
  /**
   * The dimensionality of the stored vectors (they all have the same)
   * Not the number of such vectors, use size() for this purpose
   */
  public int dim() { return dim; }
  public int size() { return featureVectors.size(); }
  public double getRegularizationFactor(int index)
  {
    return regularizationFactors[index];
  }
  /**
   * Features not found in the index are simply ignored
   * 
   * @param labeledInstance
   * @return
   */
  public SparseVector getFeatureVector(LabeledInstance<I, L> labeledInstance, boolean cache)
  {
    SparseVector vector = featureVectors.get(labeledInstance);
    if (vector == null) 
    {
      vector = extractFeatureVector(labeledInstance, indexer, extractor, false);
      if (cache)
        featureVectors.put(labeledInstance, vector);
    }
//    assert indexer.hashCode() == correctnessField;
    return vector;
  }
//  public SparseVector getFeatureVector(I instance, L label, boolean cache)
//  {
//    return getFeatureVector(new LabeledInstance<I, L>(label, instance), cache);
//  }
  @Override
  public String toString()
  {
    return featureVectors.toString();
  }
  /**
   * Uses the indexer to create a weight vector with the initialization prescribed
   * by init and zero 
   * 
   * If an entry in the counter is not in the index, it is ignored
   * @param init
   * @return
   */
  public double [] createInitialWeight(Counter init)
  {
    double [] result = new double[dim()];
    for (Object feature : init.keySet())
    {
      if (indexer.containsObject((F)feature))
      {
        int index = indexer.o2i((F)feature);
        result[index] = init.getCount(feature);
      }
    }
    return result;
  }
  public double [] createInitialWeight()
  {
    return createInitialWeight(new Counter());
  }
  /**
   * Label the weights by the corresponding feature using the internal indexer
   * @param weights
   * @return
   */
  public Counter namedCounter(double [] weights)
  {
    assert weights.length == dim();
    Counter<F> named = new Counter<F>();
    for (int i = 0; i < dim(); i++)
    {
      F name = indexer.i2o(i);
      double count = weights[i];
      named.setCount(name, count);
    }
    return named;
  }
  //
  //
  //
  /**
   * Private use only
   * @param labeledInstance
   */
  private void addFeatureVector(LabeledInstance<I, L> labeledInstance)
  {
    SparseVector vector = extractFeatureVector(labeledInstance, indexer, extractor, true);
//    assert indexer.hashCode() == correctnessField;
    featureVectors.put(labeledInstance, vector);
  }
  //
  //
  //
  private FeatureVectors(
      final FeatureExtractor<LabeledInstance<I,L>, F> extractor,
      final Indexer<F> indexer,
      final double [] regularizationFactors)
  {
    this.dim = indexer.size();
    this.indexer = indexer;
    this.extractor = extractor;
    this.regularizationFactors = regularizationFactors;
  }
  //
  // Aux static methods for creation of FeatureVector objects
  //
  private static <I,L,F> Indexer<F> computeIndexes(
      BaseMeasures<I,L> baseMeasures,
      Set<LabeledInstance<I, L>> training, 
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    Indexer<F> indexer = new Indexer<F>();
    for (LabeledInstance<I, L> labeledInstance : training)
    {
      // cross product over all possible labels
      for (L label : baseMeasures.support(labeledInstance.getInput()))
      {
        LabeledInstance<I, L> currentLabeledInstance = 
          new LabeledInstance<I, L>(label, labeledInstance.getInput());
        Counter<F> features = extractor.extractFeatures(currentLabeledInstance);
        for (F feature : features)
        {
          indexer.addToIndex(feature); //o2iEasy(feature);
        }
      }
    }
    return indexer;
  }
  private static <I,L,F> Indexer<F> computeIndexesFromSet(Set<F> features)
  {
    Indexer<F> indexer = new Indexer<F>();
    for (F feature : features)
      indexer.addToIndex(feature);
    return indexer;
  }
  private static <I,L,F> double [] computeRegFactors(
      Indexer<F> indexer, 
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
    double [] result = new double[indexer.size()];
    for (int index = 0; index < indexer.size(); index++)
    {
      F feature = indexer.i2o(index);
      result[index] = extractor.regularizationFactor(feature);
    }
    return result;
  }
  /**
   * Get a feature vector for the provided instance using the extractor
   * Note that if a feature found in the instance is not in the indexer,
   * it will be ignored if the ensureFeaturesPresent if set to false,
   * and will throw a runtime exception otherwise
   * @param <I>
   * @param <L>
   * @param <F>
   * @param instance
   * @param indexer
   * @param extractor
   * @return
   */
  private static <I,L,F> SparseVector extractFeatureVector(
      LabeledInstance<I,L> instance, 
      Indexer<F> indexer, 
      FeatureExtractor<LabeledInstance<I,L>, F> extractor,
      boolean ensureFeaturesPresent)
  {
    Counter<F> features = extractor.extractFeatures(instance);
    List<Integer> indices = new ArrayList<Integer>();
    List<Double> values = new ArrayList<Double>();
    for (F feature : features)
    {
      if (indexer.o2iEasy(feature) == -1)
      {
        if (ensureFeaturesPresent) 
          throw new RuntimeException("Unknown feature: " + feature.toString());
      }
      else
      {
        indices.add(indexer.o2i(feature));
        values.add(features.getCount(feature));
      }
    }
    return new SparseVector(indices, values);
  }
}
