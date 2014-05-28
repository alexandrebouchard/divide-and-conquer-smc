package multilevel;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.distribution.BetaDistribution;

import bayonet.distributions.Random2RandomGenerator;
import briefj.BriefIO;
import briefj.collections.Counter;



public class QuickCheck
{
  public static void main(String [] args)
  {
    Random rand = new Random(1);
    BetaDistribution beta = 
        new BetaDistribution(
            new Random2RandomGenerator(rand),
            1 + 67, 
            1 + 0, 
            BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
    double n = 0.0;
    for (double d = 1; d < 100000 ; d++)
    {
  //    BrownianModelCalculator leaf2 = BrownianModelCalculator.observation(new double[]{d}, 1, false);
  //    BrownianModelCalculator combined = leaf1.combine(leaf1, leaf2, 1, 0, false);
  //    System.out.println("" + d + "\t" + combined.logLikelihood());
      n+= Math.pow(2,-d);
      System.out.println("" + n + "\t" + beta.density(n));
    }
    
    File f = new File("data/processed/preprocessedNYSData.csv");
    Counter<String> 
      trials    = new Counter<String>(),
      successes = new Counter<String>();
    
    for (List<String> line : BriefIO.readLines(f).splitCSV())
    {
      String key = line.get(1);
      trials.incrementCount(key, Integer.parseInt(line.get(5)));
      successes.incrementCount(key, Integer.parseInt(line.get(6)));
    }
    for (String key : successes)
      System.out.println("" + key + "\t" + (successes.getCount(key)/trials.getCount(key)));
  }
}
