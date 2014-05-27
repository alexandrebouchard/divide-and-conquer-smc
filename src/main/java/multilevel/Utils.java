package multilevel;

import bayonet.distributions.Multinomial;
import bayonet.math.NumericalUtils;



public class Utils
{
  public static double ess(double [] ws)
  {
    NumericalUtils.checkIsClose(1.0, Multinomial.getNormalization(ws));
    double sumOfSqr = 0.0;
    for (double w : ws) sumOfSqr+=w*w;
    return 1.0/sumOfSqr;
  }
}
