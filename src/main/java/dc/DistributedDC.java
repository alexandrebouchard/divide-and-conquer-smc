package dc;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import bayonet.graphs.DirectedTree;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;

    // TODO: remove node replication!!

public final class DistributedDC<P, N>
{
  final DCOptions options;
  final DCProposalFactory<P, N> proposalFactory;
  final DirectedTree<N> tree;
  
  HazelcastInstance cluster;
  IMap<N, ParticlePopulation<P>> populations;
  IMap<N, Integer> numberOfUnprocessedChildren;
  ILock nucLock; // lock for numberOfUnprocessedChildren
  IExecutorService executor;
  
  private boolean started = false;
  private boolean initializing = false;
  
  @SuppressWarnings("rawtypes")
  private static DistributedDC instance = null;
  
  @SuppressWarnings("unchecked")
  public static <P,N> DistributedDC<P,N> createInstance(DCOptions options, DCProposalFactory<P, N> proposalFactory, DirectedTree<N> tree)
  {
    if (instance != null)
      throw new RuntimeException();
    instance = new DistributedDC<P,N>(options, proposalFactory, tree);
    return instance;
  }
  
  @SuppressWarnings("unchecked")
  public static <P,N> DistributedDC<P,N> getInstance()
  {
    if (instance == null)
      throw new RuntimeException("Use createInstance(..) first.");
    while (instance.initializing)
      try { Thread.sleep(1000); }
      catch (Exception e) {}
    return instance;
  }
  
  public void start()
  {
    checkNotAlreadyStarted();
    setup();
    monitor();
  }
  
  private void monitor()
  {
    while (!populations.containsKey(tree.getRoot()))
      try { Thread.sleep(1000); }
      catch (Exception e) {}
    cluster.shutdown();
  }

  private void setup()
  {
    initHazelcast();
    ILock lock = cluster.getLock("SETUP_LOCK");
    lock.lock();
    // if this was not already done by some other node
    if (numberOfUnprocessedChildren.isEmpty())
    {
      // setup numberOfUnprocessedChildren
      for (N node : tree.getNodes())
        numberOfUnprocessedChildren.put(node, tree.getChildren(node).size());
      
      // setup basic tasks
      for (DCRecursionTask<P,N> initialTask : initialTasks())
        submitTask(initialTask);
    } 
    lock.unlock();
  }

  private void initHazelcast()
  {
    this.initializing = true;
    this.cluster = Hazelcast.newHazelcastInstance(getConfig());
    this.populations = cluster.getMap("POPULATIONS");
    this.numberOfUnprocessedChildren = cluster.getMap("N_UNPROCESSED");
    this.nucLock = cluster.getLock("NUC_LOCK");
    this.executor = cluster.getExecutorService("EXECUTOR");  
    this.initializing  = false;
  }

  private List<DCRecursionTask<P,N>> initialTasks()
  {
    List<DCRecursionTask<P,N>> result = new ArrayList<>();
    for (N node : tree.getNodes())
      if (tree.isLeaf(node))
        result.add(new DCRecursionTask<P,N>(node));
    return result;
  }

  private void checkNotAlreadyStarted()
  {
    if (started)
      throw new RuntimeException();
    started = true;
  }

  private DistributedDC(DCOptions options, DCProposalFactory<P, N> proposalFactory, DirectedTree<N> tree)
  {
    this.options = options;
    this.proposalFactory = proposalFactory;
    this.tree = tree;
  }
  
  private String createClusterName()
  {
    return "Cluster{" + 
      options.getClass()  + "=" + HashCodeBuilder.reflectionHashCode(options) + "," +
      proposalFactory.getClass() + "=" + HashCodeBuilder.reflectionHashCode(proposalFactory) + "}";
  }
  
  private Config getConfig()
  {
    Config result = new Config();
    result.getGroupConfig().setName(createClusterName());
    return result;
  }

  void submitTask(DCRecursionTask<P,N> dcRecursionTask)
  {
    executor.execute(dcRecursionTask);
  }
}
