package lregression;

import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Homogenous in the sense that each label has the same support 
 * @author Alexandre Bouchard
 *
 * @param <I>
 * @param <L>
 */
public class HomogeneousBaseMeasures<I,L> implements BaseMeasures<I,L>
{
  private SortedSet<L> unmodifiableCommonBaseMeasure;
  public HomogeneousBaseMeasures(final Collection<L> labels)
  {
    unmodifiableCommonBaseMeasure = new TreeSet<L>();
    unmodifiableCommonBaseMeasure.addAll(labels);
    unmodifiableCommonBaseMeasure 
      = Collections.unmodifiableSortedSet(unmodifiableCommonBaseMeasure);
  }
  public SortedSet<L> support(I input)
  {
    return unmodifiableCommonBaseMeasure;
  }
}
