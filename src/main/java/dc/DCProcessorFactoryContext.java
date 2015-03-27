package dc;

import bayonet.graphs.DirectedTree;



public class DCProcessorFactoryContext<P,N>
{
  public final N currentNode;
  public final DirectedTree<N> tree;
  DCProcessorFactoryContext(N currentNode, DirectedTree<N> tree)
  {
    this.currentNode = currentNode;
    this.tree = tree;
  }
  
  
}