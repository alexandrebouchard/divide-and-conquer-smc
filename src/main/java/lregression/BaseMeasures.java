package lregression;

import java.io.Serializable;
import java.util.SortedSet;

public interface BaseMeasures<I,L> extends Serializable
{
  /**
   * Should return a copy or view of the labels, in a consistent order across
   * invocation
   * 
   * Will need both consistent ordering and fast membership testing, which 
   * motivates the SortedSet
   * @param input
   * @return
   */
  public SortedSet<L> support(I input);
}
