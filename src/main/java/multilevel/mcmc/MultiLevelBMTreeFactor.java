package multilevel.mcmc;

import java.util.List;

import com.beust.jcommander.internal.Lists;

import multilevel.Node;
import multilevel.io.Datum;
import multilevel.io.MultiLevelDataset;
import multilevel.smc.BrownianModelCalculator;
import multilevel.smc.DivideConquerMCAlgorithm;
import bayonet.math.SpecialFunctions;
import blang.annotations.FactorArgument;
import blang.annotations.FactorComponent;
import blang.factors.Factor;
import blang.variables.RealVariable;



public class MultiLevelBMTreeFactor implements Factor
{
  // Note: encodes the variance at internal nodes, and the imputed theta at the leaves
  @FactorArgument(makeStochastic = true)
  public final RealVariable contents = RealVariable.real(0.01);
  
  @FactorComponent
  public final FactorComponentList<MultiLevelBMTreeFactor> componentsList;
  
  public final List<MultiLevelBMTreeFactor> children;
  public final Node node;
  public final MultiLevelDataset dataset;
  
  public static class FactorComponentList<T>
  {
    @FactorComponent
    public final T item;
    
    @FactorComponent
    public final FactorComponentList<T> next;
    
    private FactorComponentList(List<T> list, int index)
    {
      this.item = list.get(index);
      int nextIndex = index + 1;
      next = nextIndex < list.size() 
          ? new FactorComponentList<T>(list, nextIndex)
          : null;
    }
    
    public FactorComponentList(List<T> list)
    {
      this(list, 0);
    }
  }
  
  private final MultiLevelBMTreeFactor parent;
  
  public MultiLevelBMTreeFactor(MultiLevelBMTreeFactor parent, MultiLevelDataset data, Node node)
  {
    this.dataset = data;
    this.parent = parent;
    this.node = node;
    children = Lists.newArrayList();
    for (Node child : data.getChildren(node))
      children.add(new MultiLevelBMTreeFactor(this, data, child));
    componentsList = children.size() > 0 ? new FactorComponentList<MultiLevelBMTreeFactor>(children) : null;
  }

  @Override
  public double logDensity()
  {
    if (parent != null)
      return parent.logDensity();
    else
    {
      try
      {
        return logBMDensity(this).logLikelihood() + logEmissionDensity(this);
      }
      catch (Exception e)
      {
        return Double.NEGATIVE_INFINITY;
      }
    }
  }

  private  double logEmissionDensity(
      MultiLevelBMTreeFactor multiLevelBMTreeFactor)
  {
    if (multiLevelBMTreeFactor.children.isEmpty())
    {
      double imputed = multiLevelBMTreeFactor.contents.getValue();
      double probab = SpecialFunctions.logistic(imputed);
      Datum observation = dataset.getDatum(multiLevelBMTreeFactor.node);
      return DivideConquerMCAlgorithm.logBinomialPr(observation.numberOfTrials, observation.numberOfSuccesses, probab);
    }
    else
    {
      double result = 0.0;
      for (MultiLevelBMTreeFactor childFactor : multiLevelBMTreeFactor.children)
        result += logEmissionDensity(childFactor);
      return result;
    }
  }

  private static BrownianModelCalculator logBMDensity(MultiLevelBMTreeFactor multiLevelBMTreeFactor)
  {
    if (multiLevelBMTreeFactor.children.isEmpty())
    {
      double imputed = multiLevelBMTreeFactor.contents.getValue();
      return BrownianModelCalculator.observation(new double[]{imputed}, 1, false);
    }
    else
    {
      List<BrownianModelCalculator> childrenCalculators = Lists.newArrayList();
      for (MultiLevelBMTreeFactor childFactor : multiLevelBMTreeFactor.children)
        childrenCalculators.add(logBMDensity(childFactor));
      if ( multiLevelBMTreeFactor.contents.getValue() <= 0.0)
        throw new RuntimeException();
      return BrownianModelCalculator.combine(childrenCalculators, multiLevelBMTreeFactor.contents.getValue());
    }
  }

}
