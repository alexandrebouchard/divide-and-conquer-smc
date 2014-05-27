package multilevel;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

import briefj.BriefIO;
import briefj.BriefLists;
import briefj.BriefMaps;



public class MultiLevelDataset
{
  private Map<String,Set<String>> children = Maps.newHashMap();
  private Map<String,Datum> data = Maps.newHashMap();
  
  public static class Datum
  {
    public final int numberOfTrials, numberOfSuccesses;

    private Datum(int numberOfTrials, int numberOfSuccesses)
    {
      this.numberOfTrials = numberOfTrials;
      this.numberOfSuccesses = numberOfSuccesses;
    }
  }
  
  /**
   * Format, csv, with first fields being the path (from root to leaf), 
   * and the last two being the number of trials and the number of successes, in this order.
   * 
   * @param file
   */
  public MultiLevelDataset(File file)
  {
    for (List<String> line : BriefIO.readLines(file).splitCSV())
    {
      List<String> path = line.subList(0, line.size() - 2);
      List<String> datum =line.subList(line.size() - 2, line.size());
      
      for (int i = 0; i < path.size() - 1; i++)
        BriefMaps.getOrPutSet(children, path.get(i)).add(path.get(i+1));
        
      data.put(BriefLists.last(path), new Datum(Integer.parseInt(datum.get(0)), Integer.parseInt(datum.get(1))));
    }
  }
}
