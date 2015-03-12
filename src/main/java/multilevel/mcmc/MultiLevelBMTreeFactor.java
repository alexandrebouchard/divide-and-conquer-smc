package multilevel.mcmc;

import java.util.List;
import java.util.Map;

import com.beust.jcommander.internal.Lists;

import multilevel.Node;
import multilevel.io.Datum;
import multilevel.io.MultiLevelDataset;
import multilevel.smc.BrownianModelCalculator;
import multilevel.smc.DivideConquerMCAlgorithm;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import bayonet.distributions.Exponential;
import bayonet.math.SpecialFunctions;
import blang.annotations.FactorArgument;
import blang.annotations.FactorComponent;
import blang.factors.Factor;
import blang.factors.FactorComponentList;
import blang.variables.RealVariable;
import briefj.OutputManager;



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
  private final MultiLevelModelOptions modelOptions;
  
  public void logSamples(int nLevels, OutputManager output, int iteration)
  {
    if (nLevels < 1)
      return;
    
    DivideConquerMCAlgorithm.logSamples(output, contents.getValue(), node.toString(), "varSample", iteration);
    
    FactorComponentList<MultiLevelBMTreeFactor> curComponent = componentsList;
    while (curComponent != null)
    {
      curComponent.item.logSamples(nLevels - 1, output, iteration);
      curComponent = curComponent.next;
    }
  }
  
  private final MultiLevelBMTreeFactor parent;
  
  public static interface Initialization
  {
    public double getLeaf(Node n);
    public double getVariance(Node n);
  }
  
  public static class InitFromSMC implements Initialization
  {
    private final Map<Node, Particle> standardSMC_sample;
    
    public InitFromSMC(Map<Node, Particle> standardSMC_sample)
    {
      this.standardSMC_sample = standardSMC_sample;
    }

    @Override
    public double getLeaf(Node node)
    {
      Particle particle = standardSMC_sample.get(node);
      return particle.message.message[0];
    }

    @Override
    public double getVariance(Node node)
    {
      Particle particle = standardSMC_sample.get(node);
      return particle.variance;
    }
  }
  
  public MultiLevelBMTreeFactor(
      MultiLevelBMTreeFactor parent, 
      MultiLevelDataset data, 
      Node node, 
      MultiLevelModelOptions modelOptions,
      Initialization init)
  {
    this.modelOptions = modelOptions;
    if (!modelOptions.useTransform)
      throw new RuntimeException();
    this.dataset = data;
    this.parent = parent;
    this.node = node;
    children = Lists.newArrayList();
    for (Node child : data.getChildren(node))
      children.add(new MultiLevelBMTreeFactor(this, data, child, modelOptions, init));
    componentsList = children.size() > 0 ? new FactorComponentList<MultiLevelBMTreeFactor>(children) : null;
    
    if (init != null)
    {
      if (data.getChildren(node).isEmpty())
        contents.setValue(init.getLeaf(node));
      else
        contents.setValue(init.getVariance(node));
    }
    else
      if (children.size() == 0)
      {
        Datum d = data.getDatum(node);
        contents.setValue(SpecialFunctions.logistic((d.numberOfSuccesses+1)/(d.numberOfTrials+2)));
      }
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
        return logVariancePriorDensity(this) + logBMDensity(this).logLikelihood() + logEmissionDensity(this);
      }
      catch (Exception e)
      {
        return Double.NEGATIVE_INFINITY;
      }
    }
  }

  private double logVariancePriorDensity(
      MultiLevelBMTreeFactor multiLevelBMTreeFactor)
  {
    double sum = 0.0;
    
    if (multiLevelBMTreeFactor.children.size() > 0)
    {
      double variance = multiLevelBMTreeFactor.contents.getValue();
      sum += modelOptions.useUniformVariance ? 
          uniformLogDensity(variance, 0.0, modelOptions.maxVarianceIfUniform) :
          Exponential.logDensity(variance, modelOptions.variancePriorRateIfExponential);
      for (MultiLevelBMTreeFactor child : multiLevelBMTreeFactor.children)
        sum += logVariancePriorDensity(child);
    }
    
    return sum;
  }
  
  public static double uniformLogDensity(double x, double min, double max)
  {
    if (x < min) return Double.NEGATIVE_INFINITY;
    if (x >= max) return Double.NEGATIVE_INFINITY;
    return - Math.log(max - min);
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
