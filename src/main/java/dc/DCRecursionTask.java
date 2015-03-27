package dc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;




public final class DCRecursionTask<P, N>  implements Runnable, Serializable
{
  private static final long serialVersionUID = 1L;
  private final N currentNode;
  private final transient DistributedDC<P, N> dc;
  
  DCRecursionTask(N currentNode)
  {
    this.currentNode = currentNode;
    this.dc = DistributedDC.getInstance();
  }

  @Override
  public void run()
  {
    try
    {
      List<N> childrenNodes = new ArrayList<>(dc.tree.getChildren(currentNode));
      List<ParticlePopulation<P>> childrenPopulations = getChildrenPopulations(childrenNodes);
      DCProposal<P, N> proposal = null;
      Random random = getRandom();
      synchronized (dc.proposalFactory) { proposal = dc.proposalFactory.build(random, currentNode, childrenNodes); }
      ParticlePopulation<P> newPopulation = DCRecursion.dcRecurse(random, dc.options, childrenPopulations, proposal);
      dc.populations.put(currentNode, newPopulation);
      N parent = dc.tree.getParent(currentNode);
      if (parent != null)
        prepareNextTask(parent);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }
  
  private void prepareNextTask(N parent)
  {
    dc.nucLock.lock();
    try
    {
      int numberOfUnprocessedChildrenForParent = dc.numberOfUnprocessedChildren.get(parent);
      numberOfUnprocessedChildrenForParent--;
      dc.numberOfUnprocessedChildren.put(parent, numberOfUnprocessedChildrenForParent);
      if (numberOfUnprocessedChildrenForParent == 0)
        dc.submitTask(new DCRecursionTask<>(parent));
    }
    finally
    {
      dc.nucLock.unlock();
    }
  }

  private Random getRandom()
  {
    final int prime = 31;
    long seed = 1;
    seed = prime * seed + dc.options.masterRandomSeed;
    seed = prime * seed + currentNode.hashCode();
    return new Random(seed);
  }
  
  private List<ParticlePopulation<P>> getChildrenPopulations(List<N> childrenNodes)
  {
    List<ParticlePopulation<P>> result = new ArrayList<>(childrenNodes.size());
    for (N child : childrenNodes)
      result.add(dc.populations.remove(child));
    return result;
  }
}
