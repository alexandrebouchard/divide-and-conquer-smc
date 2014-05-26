package bayonet.classif;

import java.io.Serializable;

import briefj.collections.Counter;



/**
 * Feature extractors process input instances into feature counters.
 * 
 * Assumed to be immutable by ParamUpdater
 *
 * @author Dan Klein
 */
public interface FeatureExtractor<I,F> extends Serializable {
  public Counter<F> extractFeatures(I instance);
  public double regularizationFactor(F feature);
}
