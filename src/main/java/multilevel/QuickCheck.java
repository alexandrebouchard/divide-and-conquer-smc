package multilevel;

import java.io.File;
import java.util.List;
import java.util.Map;

import briefj.BriefIO;
import briefj.collections.Counter;



public class QuickCheck
{
  public static void main(String [] args)
  {
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
