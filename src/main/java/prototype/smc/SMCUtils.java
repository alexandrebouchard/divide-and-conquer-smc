package prototype.smc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import bayonet.distributions.Multinomial;
import bayonet.math.NumericalUtils;
import briefj.collections.Counter;



public class SMCUtils
{
  public static double ess(double [] ws)
  {
    NumericalUtils.checkIsClose(1.0, Multinomial.getNormalization(ws));
    double sumOfSqr = 0.0;
    for (double w : ws) sumOfSqr+=w*w;
    return 1.0/sumOfSqr;
  }
  
  public static Counter<Integer> multinomialSampling(Random rand, double [] w, int nSamples)
  {
    List<Double> darts = new ArrayList<Double>(nSamples);
    for (int n = 0; n < nSamples; n++)
      darts.add(rand.nextDouble());
    Collections.sort(darts);
    Counter<Integer> result = new Counter<Integer>();
    double sum = 0.0;
    int nxtDartIdx = 0;
    for (int i = 0; i < w.length; i++)
    {
      final double curLen = w[i];
      if (curLen < 0 - NumericalUtils.THRESHOLD)
        throw new RuntimeException();
      final double right = sum + curLen;
      
      for (int dartIdx = nxtDartIdx; dartIdx < darts.size(); dartIdx++)
        if (darts.get(dartIdx) < right)
        {
          result.incrementCount(i, 1.0);
          nxtDartIdx++;
        }
        else 
          break;
      sum = right;
    }
    if (Double.isNaN(sum))
      throw new RuntimeException();
    NumericalUtils.checkIsClose(1.0, sum);
    if (result.totalCount() != nSamples)
      throw new RuntimeException();
    return result;
  }
}
