package dc;

import java.util.Collections;
import java.util.function.Function;

import org.jgrapht.UndirectedGraph;

import prototype.Node;
import xlinear.DenseMatrix;
import xlinear.Matrix;
import bayonet.graphs.DirectedTree;
import bayonet.graphs.GraphUtils;
import bayonet.marginal.DiscreteFactorGraph;
import bayonet.marginal.algo.SumProduct;



public class TestUtilities
{
  public static DirectedTree<Node> perfectBinaryTree(int depth)
  {
    Node root = new Node(Collections.singletonList("0"));
    DirectedTree<Node> result = new DirectedTree<Node>(root);
    buildTree(result, root, depth);
    return result;
  }
  
  public static void buildTree(DirectedTree<Node> result, Node currentNode, int remainingDepth)
  {
    if (remainingDepth <= 0)
      return;
    for (int i = 0; i < 2; i++)
    {
      Node child = currentNode.child("" + i);
      result.addChild(currentNode, child);
      buildTree(result, child, remainingDepth - 1);
    }
  }
  
  public static <N> double computeExactLogZ(
      DenseMatrix transition, 
      DenseMatrix prior, 
      DirectedTree<N> tree,
      Function<N, Integer> observations) 
  {
    final int nStates = transition.nCols();
    
    // build topology
    UndirectedGraph<N, ?> topology = GraphUtils.newUndirectedGraph();
    topology.addVertex(tree.getRoot());
    convertTopology(tree, tree.getRoot(), topology);
    
    // transition matrix
    double [][] transitionMatrix = new double[nStates][nStates];
    for (int s0 = 0; s0 < nStates; s0++)
      for (int s1 = 0; s1 < nStates; s1++)
        transitionMatrix[s0][s1] = transition.get(s0, s1);
    
    // build potentials
    DiscreteFactorGraph<N> result = new DiscreteFactorGraph<>(topology);
    for (N node : tree.getNodes())
    {
      if (tree.getRoot().equals(node))
      {
        double [][] pot = new double[1][nStates];
        for (int state = 0; state < nStates; state++)
          pot[0][state] = prior.get(state, 0);
        result.setUnary(node, pot);
      }
      
      if (tree.isLeaf(node))
      {
        double [][] pot = new double[1][nStates];
        pot[0][observations.apply(node)] = 1.0;
        result.setUnary(node, pot);
      }
      
      for (N child : tree.getChildren(node))
        result.setBinary(node, child, transitionMatrix);
    }
    
    SumProduct<N> sp = new SumProduct<>(result);
    
    return sp.logNormalization();
  }
  
  private static <N> void convertTopology(DirectedTree<N> tree, N node, UndirectedGraph<N, ?> topology)
  {
    for (N child : tree.getChildren(node))
    {
      topology.addVertex(child);
      topology.addEdge(node, child);
      convertTopology(tree, child, topology);
    }
  }
  
  public static void checkValidMarkovChain(Matrix transition, Matrix initial)
  {
    if (transition.nRows() != transition.nCols() || 
        initial.nRows() != transition.nRows() ||
        initial.nCols() != 1)
      throw new RuntimeException();
    // TODO: check rows sum to one
  }
  
  private TestUtilities() {}
}
