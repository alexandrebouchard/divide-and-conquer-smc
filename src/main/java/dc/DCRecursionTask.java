package dc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;




public final class DCRecursionTask<P, N>  implements Runnable, Serializable
{
  private static final long serialVersionUID = 1L;
  private final N currentNode;
  
  DCRecursionTask(final N currentNode)
  {
    this.currentNode = currentNode;
  }
  
  private DistributedDC<P, N> dc()
  {
    return DistributedDC.getInstance();
  }

  @Override
  public void run()
  {
    try
    {
      final List<N> childrenNodes = new ArrayList<>(dc().tree.getChildren(currentNode));
      final List<ParticlePopulation<P>> childrenPopulations = getChildrenPopulations(childrenNodes);
      DCProposal<P, N> proposal = null;
      final Random random = getRandom();
      final DistributedDC<P, N> dc = dc();
      synchronized (dc().proposalFactory) { proposal = dc.proposalFactory.build(random, currentNode, childrenNodes); }
      final ParticlePopulation<P> newPopulation = DCRecursion.dcRecurse(random, dc.options, childrenPopulations, proposal);
      dc.populations.put(currentNode, newPopulation);
      final N parent = dc.tree.getParent(currentNode);
      if (parent != null)
        prepareNextTask(parent);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }
  
  private void prepareNextTask(final N parent)
  {
    final DistributedDC<P, N> dc = dc();
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
    final DistributedDC<P, N> dc = dc();
    final int prime = 31;
    long seed = 1;
    seed = prime * seed + dc.options.masterRandomSeed;
    seed = prime * seed + currentNode.hashCode();
    return new Random(seed);
  }
  
  private List<ParticlePopulation<P>> getChildrenPopulations(final List<N> childrenNodes)
  {
    final DistributedDC<P, N> dc = dc();
    final List<ParticlePopulation<P>> result = new ArrayList<>(childrenNodes.size());
    for (N child : childrenNodes)
      result.add(dc.populations.remove(child));
    return result;
  }
}
