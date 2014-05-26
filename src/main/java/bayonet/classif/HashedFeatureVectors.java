package bayonet.classif;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import bayonet.math.SparseVector;
import briefj.collections.Counter;

 

public class HashedFeatureVectors<I,L,F> implements FeatureVectorsInterface<I,L>
{

  private final FeatureExtractor<LabeledInstance<I,L>, F> extractor;
  
  private final int m; // m is the dimension of the reduced (hashed) space
  private final int n; // number of training instances
  
  // we will set the regularization factor to 1.0 for now
  // private final double [] regularizationFactors;
	
  private HashedFeatureVectors(final FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
	  this.extractor = extractor;
	  m = 1400000; // m is to be determined based on the user's memory requirements
	  n = 0;
  }
	
  public static <I,L,F> HashedFeatureVectors<I,L,F> createFeatureVectors(
      FeatureExtractor<LabeledInstance<I,L>, F> extractor)
  {
  	HashedFeatureVectors<I, L, F> result = new HashedFeatureVectors<I, L, F>(extractor);
    return result;
  }

  @Override
  public double[] createInitialWeight()
  {
  	// create empty weights
  	return createInitialWeight(new Counter());  
  }

  /**
   * Use the hash function to map the feature, otherwise 
   * similar to FeatureVectors.createInitialWeight(Counter init),
   * subtle difference is that this method keySet() method returns set 
   * of Integers and hence, using a Counter formed with String keySet() 
   * will cause the program to crash (e.g. when Counter formed from FeatureVectors.java
   * is saved and if it were to be used on this method, it will crash)
   * 
   * @param init
   * @return
   */
  @Override
  public double[] createInitialWeight(Counter init)
  {
  	double [] result = new double[dim()];
  	
  	for (Object feature : init.keySet()) {
  		int index = ((Integer)feature).intValue();
  		result[index] = init.getCount(feature);
  	}
    return result;
  }

  // dimension of the weight vector in the hashed space
  @Override
  public int dim()
  {
    return m;
  }

	@Override
  public SparseVector getFeatureVector(LabeledInstance<I, L> labeledInstance,
      boolean cache) // can ignore that for now
  {
  	// unlike the FeatureVectors.java, we compute the SparseVector on-the-fly (no caching for now)
  	Counter<F> counter = extractor.extractFeatures(labeledInstance);
  	HashMap<Integer, Double> map = new HashMap<Integer, Double>();
  	  	  	
  	for (F feature : counter) {

  		// the problem with hashing is that hash function might map to the same index
  		// more than once, in this case, we need to update the value and not overwrite it
  		
  		// NOTE: we are experimenting with two hash functions, hashing into the same space
  		// the first hash function is the hashCode() method of the feature appended by a string _h1
  		// the second hash function is the hashCode() method of the feature appended by a string _h2
  		String feature1 = feature.toString() + "_h1";

  		// in the experiments that we will be doing, F is String so we will just use the default hashcode() method
  		// hashCode() may return a negative value due to overflow, TODO: how to deal with this issue? for now just take the absolute value
  		Integer key1 = new Integer(Math.abs(feature1.hashCode()) % dim());
  		double existingValue = 0.0;
  		if (map.containsKey(key1)) {
  			existingValue = map.remove(key1).doubleValue();
  		}
  		
  		Double val1 = new Double(existingValue + counter.getCount(feature));
  		map.put(key1, val1);

  		// is prepending h2_ really makes this hash function independent of the first one, which is appended by _h1?
  		String feature2 = "h2_" + feature.toString();
  		Integer key2 = new Integer(Math.abs(feature2.hashCode()) % dim());
  		existingValue = 0.0;
  		if (map.containsKey(key2)) {
  			existingValue = map.remove(key2).doubleValue();
  		}
  		
  		Double val2 = new Double(existingValue + counter.getCount(feature));
  		map.put(key2, val2);

  	}
  	
  	//SparseVector sparseVector = new SparseVector(indices, values, dim());
  	SparseVector sparseVector = new SparseVector(map, dim());
    return sparseVector;
  }

  @Override
  public double getRegularizationFactor(int index)
  {
  	// for now, we will not be concerned with regularization factor and just return 1.0
    return 1.0;
  }

  @Override
  public Counter namedCounter(double[] weights)
  {
  	assert weights.length == dim();
  	// key for each Counter element is an Integer since the features are mapped to an integer
  	// to live in the hashed space
  	Counter counter = new Counter<Integer>();
  	for (int i = 0; i < dim(); i += 1) {
  		counter.setCount(new Integer(i), weights[i]);
  	}
    return counter;
  }

  public int size()
  {
    return n;
  }

  // return -1 or +1
  private double xi(F feature) 
  {
  	/*
  	int hashCode = Math.abs(feature.hashCode());
  	double retval = (-2*(hashCode % 2) + 1);
  	return retval;
  	*/
  	return 1.0;
  }
  
}
