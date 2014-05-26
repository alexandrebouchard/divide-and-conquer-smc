package bayonet.classif;

import bayonet.math.SparseVector;
import briefj.collections.Counter;



public interface FeatureVectorsInterface<I,L>
{
  /**
   * dim of weight vectors
   */
  public int dim();
  
//  /**
//   * Number of feature-distinct training instances
//   */
//  public int size();
  
  /**
   * Get the regul. penalty coef for one of the weight coordinates
   */
  public double getRegularizationFactor(int index);
  
  /**
   * 
   */
  public SparseVector getFeatureVector(LabeledInstance<I, L> labeledInstance, boolean cache);
  
  /**
   * Initial weight vector of the correct size
   */
  public double [] createInitialWeight();
  
  
  /**
   * Save the weights into a counter
   */
  public Counter namedCounter(double [] weights);
  
  /**
   * Load weights from a counter and put it in array form
   */
  public double [] createInitialWeight(Counter init);
}
