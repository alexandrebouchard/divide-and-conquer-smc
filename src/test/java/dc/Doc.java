package dc;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import multilevel.Node;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.UndirectedGraph;
import org.junit.Test;

import bayonet.graphs.DirectedTree;
import bayonet.graphs.GraphUtils;
import bayonet.marginal.DiscreteFactorGraph;
import bayonet.marginal.algo.SumProduct;
import tutorialj.Tutorial;
import xlinear.DenseMatrix;
import xlinear.Matrix;
import xlinear.MatrixOperations;

public class Doc
{
  /**
   * 
   * Summary
   * -------
   * 
   * Two implementations of Divide and Conquer Sequential Monte Carlo (DC SMC), described in our 
   * [arXiv preprint.](http://arxiv.org/abs/1406.4993)
   * 
   * 1. One implementation has a specific model hard coded in it, namely the hierarchical model 
   *    with a binomial emission model used in section 5.2 of the above pre-print.
   * 2. A second implementation offers convenient interfaces to apply the algorithm to other models. 
   *    This second implementation also offers distributed and parallel computing functionalities.
   *    This second implementation is used in section 5.3 of the above pre-print.
   * 
   * The paper explaining this method is under review. Please contact us if you would like to use this software.
   * 
   * 
   * Installation
   * ------------
   * 
   * There are three ways to install:
   * 
   * ### Integrate to a gradle script
   * 
   * Simply add the following lines (replacing 1.0.0 by the current version (see git tags)):
   * 
   * ```groovy
   * repositories {
   *  mavenCentral()
   *  jcenter()
   *  maven {
   *     url "http://www.stat.ubc.ca/~bouchard/maven/"
   *   }
   * }
   * 
   * dependencies {
   *   compile group: 'ca.ubc.stat', name: 'multilevelSMC', version: '1.0.0'
   * }
   * ```
   * 
   * ### Compile using the provided gradle script
   * 
   * - Check out the source ``git clone git@github.com:alexandrebouchard/multilevelSMC.git``
   * - Compile using ``gradle installApp``
   * - Add the jars in ``build/install/multilevelSMC/lib/`` into your classpath
   * 
   * ### Use in eclipse
   * 
   * - Check out the source ``git clone git@github.com:alexandrebouchard/multilevelSMC.git``
   * - Type ``gradle eclipse`` from the root of the repository
   * - From eclipse:
   *   - ``Import`` in ``File`` menu
   *   - ``Import existing projects into workspace``
   *   - Select the root
   *   - Deselect ``Copy projects into workspace`` to avoid having duplicates
   *   
   *   
   * Running DC SMC on the binary emission hierarchical model (implementation 1)
   * ---------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-simple.sh -inputData data/preprocessedNYSData.csv -nParticles 1000``. 
   * - Note: for the plots to work, you need to have R in your PATH variable (more precisely, Rscript) 
   * - Various output files are written in ``results/latest/``
   * 
   * The main interest of this implementation is for replicating the results in section 5.2 and 5.3 of 
   * our pre-print. To do so, see the following [separate public repository](https://github.com/alexandrebouchard/multilevelSMC-experiments) 
   * which contains the exact sets of options used to run the experiments as well as the plotting scripts.
   * To extend DC SMC to other models, use the second implementation instead, described next.
   * 
   * 
   * Running parallel and distributed DC SMC on the binary emission hierarchical model (implementation 2)
   * ----------------------------------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv``
   * 
   * ### Distributed and parallel computing options
   * 
   * To run in a distributed fashion, simply issue the same command line on different machines. The machines will 
   * discover each other (via multicast) and distribute the work dynamically. You can see in the output a variable 
   * ``nWorkers=[integer]`` showing the number of nodes cooperating. For testing purpose, you can do this on
   * one machine by opening two terminals (however, there is a better way to get multi-threading, see below).
   * 
   * The machines will only cooperate if all the command line options match exactly. You can check for example that running 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 2000`` will not cooperate with 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 1000``. You can also force groups 
   * of machines to avoid cooperation by using the ``-clusterSubGroup [integer]``
   * 
   * To use multiple threads within one machine, use ``-nThreadsPerNode [integer]``. This can be used in conjunction with 
   * a distributed computation, or without. 
   * 
   * 
   * ### Additional options
   * 
   * If you want the machines to wait each other, 
   * use ``-minimumNumberOfClusterMembersToStart [integer]``. This will cause machines to wait to start until this number 
   * of machines is gathered OR until ``maximumTimeToWaitInMinutes`` minutes has elapsed. 
   * Whether these options is used or not, machines can always join at any point in the execution of the algorithm. 
   * The waiting options were used for wall-clock 
   * running time analysis purpose. In most cases, these options can be ignored.
   * 
   * When the number of nodes in the DC-SMC tree is much larger than the number of threads times the number of nodes, 
   * it is useful to 
   * coarsen the granularity of the subtasks. To do so, you can use ``-maximumDistributionDepth [integer]``, which alters the 
   * way subtasks are created as follows: instead of the basic initial tasks being the leaves of the tree, they are the 
   * subtrees rooted at a depth from the root given by ``maximumDistributionDepth``. The subtrees under are computed 
   * serially within each node/thread. 
   * 
   * 
   * Using parallel and distributed DC SMC with your model
   * -----------------------------------------------------
   * 
   * First, build a class to encode each individual particle. The only requirement is that it should implement 
   * ``Serializable``. This will become generic type ``P`` in the following. 
   * 
   * Second, build a class to encode nodes in the tree. The only requirement is that you should override hashcode 
   * and equals to ensure that each node in the tree be unique (i.e. not .equals(..) with any other node, 
   * they will be inserted in a hashtable), AND reproducible (i.e. the default implementation of hashcode will 
   * depend on the memory location and will be different from machine to machine). A reasonable default implementation 
   * is in ``multilevel.Node``. This will become generic type ``N`` in the following. 
   * 
   * Next, the main step consists in providing code that proposes, i.e. merges sub-populations. This is done by 
   * creating a class implementing ``dc.DCProposal``. This class will be responsible for both proposing, and 
   * providing a LOG weight update for the proposal (including taking care of computing the ratio in step 2(c) 
   * of Algorithm 2 in the arXiv pre-print). 
   * 
   * Here is an example, based on a simple model where transitions are provided 
   */
  @Tutorial(startTutorial = "README.md", showSource = true, showSignature = true)
  public DCProposal<Integer> markovChainNaiveProposal(Matrix transition, Matrix prior) 
  {
    final int nStates = transition.nRows();
    checkValidMarkovChain(transition, prior);
    // Model: a finite state Markov chain, with..
    //   transition, a n by n transition matrix
    //   initial, a n by 1 initial distribution (i.e. prior on root)
    // NB: this is just for illustration/testing purpose as one can do exact inference 
    //     on this model
    return new DCProposal<Integer>() { // In this example, particles are just integers
      /**
       * Propose a parent particle given the children. 
       * All the randomness should be obtained via the provided random object.
       * 
       * @param random
       * @param childrenParticles
       * @return A pair containing the LOG weight update and the proposed particle
       *    In the basic algorithm, this is given by gamma(x') / q(x'|x_1, .., x_C) / gamma(x_1) / .. / gamma(x_C)
       *    Where x' is the tree, and x_1, .., x_C are the C children subtrees.
       */
      @Override
      public Pair<Double, Integer> propose(Random random, List<Integer> childrenParticles)
      {
        // Naive proposal: uniform
        Integer proposal = random.nextInt(nStates);
        double weightUpdate = nStates; // 1 / q(x'|x), where q(x'|x) = 1/nStates
        weightUpdate *= prior.get(proposal, 0); // prior(proposal) is part of gamma(x')
        for (Integer childState : childrenParticles)
        {
          weightUpdate *= transition.get(proposal, childState); // transition(child | proposal) is part of gamma(x')
          weightUpdate /= prior.get(proposal, 0); // everything else in gamma(x_c) gets cancelled with gamma(x')
        }
        return Pair.of(Math.log(weightUpdate), proposal);
      }
    };
  }
  
  /**
   * Here is an example of how to put it all together:
   */
  @Test
  @Tutorial(showSource = true, showLink = true, linkPrefix = "src/test/java")
  public void testMarkovChainExample()
  {
    System.out.println(transition);
    // 2 x 2 dense matrix
    //       0         1       
    // 0 |   0.800000  0.200000
    // 1 |   0.200000  0.800000
    
    System.out.println(prior);
    // 2 x 1 dense matrix
    // 0       
    // 0 |   0.500000
    // 1 |   0.500000
    
    DCProposalFactory<Integer,Node> factory = new DCProposalFactory<Integer,Node>() 
    {
      @Override
      public DCProposal<Integer> build(
          Random random, 
          Node currentNode,
          List<Node> childrenNodes)
      {
        if (childrenNodes.size() == 0)
          // In this toy example, we set the leaves as observed and equal to 0
          // This is modelled as a proposal that always returns state 0 if this 
          // is a leaf.
          return (rand, children) -> Pair.of(Math.log(prior.get(0, 0)), 0);
        else
          // Else, return the proposal defined above
          return markovChainNaiveProposal(transition, prior);
      }
    };
    
    // Topology, here a simple perfect binary tree of depth 3
    final DirectedTree<Node> tree = perfectBinaryTree(3);
    
    // Use DCOptions to set option, either programmatically as below, 
    // or see DDCMain for an example how to parse these options from command line.
    DCOptions options = new DCOptions();
    options.nThreadsPerNode = 2;
    
    // Prepare the simulation
    DistributedDC<Integer, Node> instance = DistributedDC.createInstance(options, factory, tree);
    
    // By default, the processor below is included, which prints and records in the result folder 
    // some basic statistics like ESS, logZ estimates, etc. I.e. the line below is not needed:
    //   instance.addProcessorFactory(new DefaultProcessorFactory<>());
    
    // You can implement ProcessorFactory and add it to the instance for more detailed 
    // processing of the particles and their weights. 
    
    // Perform the  sampling
    instance.start();
    // timing: node=0, ESS=553.8295719155343, rESS=0.5538295719155343, logZ=-3.62525779010553, nWorkers=1, iterationProposalTime=1, globalTime=85
    
    // Compare to exact log normalization obtained by sum product:
    System.out.println("exact = " + computeExactLogZ(transition, prior, tree, (node) -> 0));
    // exact = -3.600962588536195
  }
  
  public static DirectedTree<Node> perfectBinaryTree(int depth)
  {
    Node root = new Node(Collections.singletonList("0"));
    DirectedTree<Node> result = new DirectedTree<Node>(root);
    buildTree(result, root, depth);
    return result;
  }
  
  private static void buildTree(DirectedTree<Node> result, Node currentNode, int remainingDepth)
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
  
  private static <N> double computeExactLogZ(
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
  
  private DenseMatrix transition = MatrixOperations.denseCopy(new double [][] {
    {0.8, 0.2},
    {0.2, 0.8}
  });
  
  private DenseMatrix prior = MatrixOperations.denseCopy(new double[] {0.5, 0.5});
  
  private void checkValidMarkovChain(Matrix transition, Matrix initial)
  {
    if (transition.nRows() != transition.nCols() || 
        initial.nRows() != transition.nRows() ||
        initial.nCols() != 1)
      throw new RuntimeException();
    // TODO: check rows sum to one
  }
  
  // push maven!
  // add some basic doc in the separate repo on experiments multilevelSMC-experiments
}
