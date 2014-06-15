package multilevel.io;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import multilevel.Node;

import com.google.common.collect.Maps;

import briefj.BriefIO;
import briefj.BriefMaps;
import briefj.collections.Counter;



public class MultiLevelDataset
{
  private Map<Node,Set<Node>> children = Maps.newHashMap();
  public final Map<Node,Datum> data = Maps.newHashMap();
  private Node root = null;
  
  public Set<Node> getChildren(Node node)
  {
    if (!children.containsKey(node))
      return Collections.emptySet();
    return children.get(node);
  }
  
  public Node getRoot() 
  {
    return root;
  }
  
  public Datum getDatum(Node node)
  {
    return data.get(node);
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
      
      if (root == null)
        root = Node.root(path.get(0)); 
      
      for (int i = 0; i < path.size() - 1; i++)
        BriefMaps.getOrPutSet(children, new Node(path.subList(0, i+1))).add(new Node(path.subList(0, i+2))); 
        
      data.put(new Node(path), new Datum(Integer.parseInt(datum.get(0)), Integer.parseInt(datum.get(1))));
    }
  }
  
  public static void main(String [] args)
  {
    MultiLevelDataset data = new MultiLevelDataset(new File("data/processed-v2/preprocessedNYSData.csv"));
    Counter<Integer> nNodes = new Counter<Integer>();
    data.nNodes(nNodes, data.root, 0);
    System.out.println("Nodes per level: " + nNodes);
    System.out.println("Total number of levels: " + nNodes.totalCount());
    int nStudents = 0;
    for (Datum d : data.data.values())
      nStudents += d.numberOfTrials;
    System.out.println("Total number of students: " + nStudents);
  }
  
  private void nNodes(Counter<Integer> c, Node n, int level)
  {
    c.incrementCount(level, 1.0);
    for (Node ch : getChildren(n))
      nNodes(c, ch, level + 1);
  }
}
