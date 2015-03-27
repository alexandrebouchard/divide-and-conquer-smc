package dc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;




final class DCRecursionTask<P, N>  implements Runnable, Serializable
{
  private static final long serialVersionUID = 1L;
  private final N currentNode;
  private final boolean areChildrenPopulationsFromCluster;
  
  DCRecursionTask(final N currentNode, boolean areChildrenPopulationsFromCluster)
  {
    this.currentNode = currentNode;
    this.areChildrenPopulationsFromCluster = areChildrenPopulationsFromCluster;
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
      final DistributedDC<P, N> dc = dc();
      final ParticlePopulation<P> newPopulation = run(currentNode, areChildrenPopulationsFromCluster);
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
  
  private ParticlePopulation<P> run(N node, boolean areChildrenPopulationsFromCluster)
  {
    final DistributedDC<P, N> dc = dc();
    final List<N> childrenNodes = new ArrayList<>(dc.tree.getChildren(node));
    final List<ParticlePopulation<P>> childrenPopulations = areChildrenPopulationsFromCluster ?
        getChildrenPopulationsFromCluster(childrenNodes) : 
        getChildrenPopulationsRecursively(childrenNodes);
    DCProposal<P> proposal = null;
    List<DCProcessor<P>> processors = null;
    final Random random = getRandom(node);
    
    synchronized (dc.proposalFactory) 
    { 
      proposal = dc.proposalFactory.build(random, node, childrenNodes); 
      processors = dc.processorFactory.build(new DCProcessorFactoryContext<P,N>(node, dc.tree));
    }
    return DCRecursion.dcRecurse(random, dc.options, childrenPopulations, proposal, processors);
  }
  
  private List<ParticlePopulation<P>> getChildrenPopulationsRecursively(
      List<N> childrenNodes)
  {
    List<ParticlePopulation<P>> result = new ArrayList<>();
    for (N childNode : childrenNodes)
      result.add(run(childNode, false));
    return result;
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
        dc.submitTask(new DCRecursionTask<>(parent, true));
    }
    finally
    {
      dc.nucLock.unlock();
    }
  }

  /**
   * @return A random generator approximately unique to this node and master random.
   */
  private Random getRandom(N node)
  {
    final DistributedDC<P, N> dc = dc();
    final int prime = 31;
    long seed = 1;
    seed = prime * seed + dc.options.masterRandomSeed;
    seed = prime * seed + node.hashCode();
    return new Random(seed);
  }
  
  private List<ParticlePopulation<P>> getChildrenPopulationsFromCluster(final List<N> childrenNodes)
  {
    final DistributedDC<P, N> dc = dc();
    final List<ParticlePopulation<P>> result = new ArrayList<>(childrenNodes.size());
    for (N child : childrenNodes)
      result.add(dc.populations.remove(child)); // get and remove at same time (won't be needed anymore)
    return result;
  }
}
