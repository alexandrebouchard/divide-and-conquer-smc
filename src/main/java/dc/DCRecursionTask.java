package dc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;




final class DCRecursionTask<P, N>  implements Runnable, Serializable
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
      DCProposal<P> proposal = null;
      List<DCProcessor<P>> processors = null;
      final Random random = getRandom();
      final DistributedDC<P, N> dc = dc();
      synchronized (dc().proposalFactory) 
      { 
        proposal = dc.proposalFactory.build(random, currentNode, childrenNodes); 
        processors = dc.processorFactory.build(new DCProcessorFactoryContext<P,N>(currentNode, dc().tree));
      }
      final ParticlePopulation<P> newPopulation = DCRecursion.dcRecurse(random, dc.options, childrenPopulations, proposal, processors);
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

  /**
   * @return A random generator approximately unique to this node and master random.
   */
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
      result.add(dc.populations.remove(child)); // get and remove at same time (won't be needed anymore)
    return result;
  }
}
