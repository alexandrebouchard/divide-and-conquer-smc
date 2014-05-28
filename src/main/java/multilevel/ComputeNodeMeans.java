package multilevel;

import java.io.File;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;



public class ComputeNodeMeans
{
  public static void main(String [] args)
  {
    MultiLevelDataset dataset = new MultiLevelDataset( 
//        new File( "data/tiny.csv"));
    new File("data/processed/preprocessedNYSData.csv"));
    
    computeAverages(dataset,dataset.getRoot());
    
  }

  private static double computeAverages(MultiLevelDataset dataset, Node node)
  {
    SummaryStatistics childrenStats = new SummaryStatistics();
    
    if (dataset.getChildren(node).isEmpty())
    {
      Datum d = dataset.getDatum(node);
      return d.numberOfSuccesses / ((double) d.numberOfTrials);
    }
    else
      for (Node child : dataset.getChildren(node))
        childrenStats.addValue(computeAverages(dataset, child)); 
    
    if (node.level() == 1)
    {
      System.out.println(node);
      System.out.println("" + childrenStats.getMean() + " +/- " + childrenStats.getStandardDeviation());
      System.out.println();
    }
    
    return childrenStats.getMean();
  }
}
