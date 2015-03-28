package dc;

import bayonet.graphs.DirectedTree;



public class DCProcessorFactoryContext<P,N>
{
  public final N currentNode;
  public final DirectedTree<N> tree() { return dc.tree; }
  public final DistributedDC<P, N> dc;
  DCProcessorFactoryContext(N currentNode, DirectedTree<N> tree)
  {
    this.currentNode = currentNode;
    this.dc = DistributedDC.getInstance();
  }
}