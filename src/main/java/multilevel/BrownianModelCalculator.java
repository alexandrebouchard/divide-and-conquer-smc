package multilevel;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.beust.jcommander.internal.Lists;

import bayonet.distributions.Normal;
import briefj.opt.Option;


public class BrownianModelCalculator
{
  public static BrownianModelCalculator combine(List<BrownianModelCalculator> children, double variance)
  {
    if (children.size() < 2) throw new RuntimeException("Supports only two or more children (the case nChildren == 1 can be computed analytically)");
    LinkedList<BrownianModelCalculator> queue = Lists.newLinkedList(children);
    BrownianModelCalculator 
      first = queue.poll(),
      second= queue.poll();
    BrownianModelCalculator 
      current = first.combine(first, second, variance, variance, false);
    while (!queue.isEmpty())
    {
      BrownianModelCalculator next = queue.poll();
      current = current.combine(current, next, 0.0, variance, false);
    }
    return current;
  }
  
  
  
  @Option public static boolean useVarianceTransform = false;
  public final boolean resampleRoot;
  
  // sites X {mean,variance}
  public final double[] message;
  public final double messageVariance;
  private final double loglikelihood;
  public double lognorm;
  private final int nsites;
  
  private BrownianModelCalculator (double[] message, double messageVariance, int nSites, double loglikelihood, boolean resampleRoot) {
    this.message = message;
    this.messageVariance = messageVariance;
    this.loglikelihood = loglikelihood;
    this.nsites = nSites;
    this.lognorm = 0;
    this.resampleRoot = resampleRoot;
  }
  private BrownianModelCalculator (double[] message, double messageVariance, int nSites, double loglikelihood, double lognorm, boolean resampleRoot) {
	    this.message = message;
	    this.messageVariance = messageVariance;
	    this.loglikelihood = loglikelihood;
	    this.nsites = nSites;
	    this.lognorm = lognorm;
	    this.resampleRoot = resampleRoot;
	  }
  
  public static BrownianModelCalculator observation (double[] observed, int nSites, boolean resampleRoot) {
    return observation(observed, nSites, useVarianceTransform, resampleRoot);
  }
  private static BrownianModelCalculator observation (double[] observed, int nSites, boolean useVarianceTransform, boolean resampleRoot) {
    if (observed.length != nSites )
      throw new RuntimeException();
    double[] state = getNewMessage (nSites);
    for (int i = 0 ; i < nSites; i++) {
     if (useVarianceTransform) // Delta method !!
        state[i] = Math.asin(Math.sqrt(observed[i]));
      else // assumes perfect observations
        state[i] = observed[i];
    }
    return new BrownianModelCalculator (state,0.0, nSites, 0, resampleRoot);
  }

  private final Object calculate(
      final BrownianModelCalculator l1, final BrownianModelCalculator l2, 
      final double v1, final double v2,
      final boolean peek) 
  {

    final double[] message  = (peek ? null : getNewMessage(nsites));
    double logl = 0;
    final double var1 = l1.messageVariance; // l1.message[i][1];
    final double var2 = l2.messageVariance; //l2.message[i][1];
    final double var = 1/(var1+v1) + 1/(var2+v2);
    final double newMessageVariance = 1/var;
    for (int i = 0 ; i < nsites; i++) 
    {
      final double mean1 = l1.message[i];
      final double mean2 = l2.message[i];

      if (!peek)
        message[i] = ( (mean1)/(var1+v1) + (mean2)/(var2+v2) ) / var;
      
      final double cur  = logNormalDensity (mean1-mean2,0,(v1+var1+v2+var2));
      
      logl += cur;
    }
    double lognorm = logl;
    logl += l1.loglikelihood + l2.loglikelihood; 
    
    if (peek) return logl;
    else      return (resampleRoot ? resampleRoot(message, newMessageVariance, nsites) : new BrownianModelCalculator(message, newMessageVariance,  nsites, logl, lognorm, resampleRoot)); 
  }
  private static Random rand = new Random(1);
  private static BrownianModelCalculator resampleRoot(double[] message,
      double newMessageVariance, int nSites)
  {
    for (int i = 0; i < message.length; i++)
      message[i] = Normal.generate(rand, message[i], newMessageVariance);
    return observation(message, nSites, false, true);
  }

  public BrownianModelCalculator combine(BrownianModelCalculator node1,
      BrownianModelCalculator node2, double delta1, double delta2, boolean doNotBuildCache)
  {
    return (BrownianModelCalculator) calculate(node1, node2, delta1, delta2, false);
  }

  public double peekCoalescedLogLikelihood(BrownianModelCalculator node1,
      BrownianModelCalculator node2, double delta1, double delta2)
  {
    return (Double) calculate(node1, node2, delta1, delta2, true);
  }
  
  public static final double logNormalDensity (final double x, final double mean, final double var) {
    return -0.5*(x-mean)*(x-mean)/var -0.5*Math.log(2*Math.PI * var);
  }

  public double logLikelihood() {
    return loglikelihood;
  }
  
  private static double[] getNewMessage (final int nsites) {
    return new double[nsites];
  }
}
