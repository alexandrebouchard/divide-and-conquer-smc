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
  private Map<Node,Set<Node>> children = Maps.newHashMap();
  private Map<Node,Datum> data = Maps.newHashMap();
  
  public static class Datum
  {
    public final int numberOfTrials, numberOfSuccesses;

    private Datum(int numberOfTrials, int numberOfSuccesses)
    {
      this.numberOfTrials = numberOfTrials;
      this.numberOfSuccesses = numberOfSuccesses;
    }
  }
  
  public static class Node
  {
    public final int level;
    public final String label;
    
    private Node(int level, String label)
    {
      super();
      this.level = level;
      this.label = label;
    }
    @Override
    public int hashCode()
    {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((label == null) ? 0 : label.hashCode());
      result = prime * result + level;
      return result;
    }
    @Override
    public boolean equals(Object obj)
    {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Node other = (Node) obj;
      if (label == null)
      {
        if (other.label != null)
          return false;
      } else if (!label.equals(other.label))
        return false;
      if (level != other.level)
        return false;
      return true;
    }
    @Override
    public String toString()
    {
      return "Level" + level + ":" + label + "]";
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
        BriefMaps.getOrPutSet(children, new Node(i, path.get(i))).add(new Node(i+1, path.get(i+1)));
        
      data.put(new Node(path.size()-1, BriefLists.last(path)), new Datum(Integer.parseInt(datum.get(0)), Integer.parseInt(datum.get(1))));
    }
  }
  
  public static void main(String [] args)
  {
    MultiLevelDataset data = new MultiLevelDataset(new File("data/processed/preprocessedNYSData.csv"));
    System.out.println(data.children);
  }
}
