package dc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.time.StopWatch;

import bayonet.graphs.DirectedTree;
import briefj.BriefIO;
import briefj.repo.RepositoryUtils;
import briefj.repo.VersionControlRepository;
import briefj.run.Results;

import com.hazelcast.config.Config;
import com.hazelcast.config.ExecutorConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;

/*
 * TODO:
 *  - recovery of the task if one of the nodes crashes
 *  - run on pre-terminals
 */

public final class DistributedDC<P, N>
{
  final DCOptions options;
  final DCProposalFactory<P, N> proposalFactory;
  final DirectedTree<N> tree;
  final DCProcessorFactory<P, N> processorFactory;
  
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
  public static <P,N> DistributedDC<P,N> createInstance(
      DCOptions options, 
      DCProposalFactory<P, N> proposalFactory, 
      DirectedTree<N> tree,
      DCProcessorFactory<P, N> processorFactory)
  {
    if (instance != null)
      throw new RuntimeException();
    instance = new DistributedDC<P,N>(options, proposalFactory, tree, processorFactory);
    return instance;
  }
  
  @SuppressWarnings("unchecked")
  public static <P,N> DistributedDC<P,N> getInstance()
  {
    if (instance == null)
      throw new RuntimeException("Use createInstance(..) first.");
    while (instance.initializing)
      sleep();
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
    StopWatch workTime = new StopWatch();
    workTime.start();
    while (!populations.containsKey(tree.getRoot()))
      sleep();
    record(workTime);
    cluster.shutdown();
  }

  private void record(StopWatch workTime)
  {
    BriefIO.write(Results.getFileInResultFolder("workTime"), "" + workTime.getTime());
    BriefIO.write(Results.getFileInResultFolder("nWorkers"), "" + cluster.getCluster().getMembers().size());
  }

  private void setup()
  {
    initHazelcast();
    waitForEnoughWorkers();
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

  private void waitForEnoughWorkers()
  {
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    while (
        cluster.getCluster().getMembers().size() < options.minimumNumberOfClusterMembersToStart &&
        stopWatch.getTime()/1000/60 < options.maximumTimeToWaitInMinutes)
      sleep();
  }

  private void initHazelcast()
  {
    this.initializing = true;
    this.cluster = Hazelcast.newHazelcastInstance(getConfig());
    this.populations = cluster.getMap(POPULATION_MAP_NAME);
    this.numberOfUnprocessedChildren = cluster.getMap("N_UNPROCESSED");
    this.nucLock = cluster.getLock("NUC_LOCK");
    this.executor = cluster.getExecutorService("EXECUTOR");  
    this.initializing  = false;
  }

  private List<DCRecursionTask<P,N>> initialTasks()
  {
    List<DCRecursionTask<P,N>> result = new ArrayList<>();
    populateInitialTasks(result, tree.getRoot(), options.maximumDistributionDepth);
    System.out.println("nInitialTasks=" + result.size());
    return result;
  }

  private void populateInitialTasks(
      List<DCRecursionTask<P, N>> result, // mod in place 
      final N node,
      final int maximumDistributionDepth)
  {
    if (tree.isLeaf(node) || maximumDistributionDepth == 0)
      result.add(new DCRecursionTask<P,N>(node, false));
    else
      for (N childrenNode : tree.getChildren(node))
        populateInitialTasks(result, childrenNode, maximumDistributionDepth - 1);
  }

  private void checkNotAlreadyStarted()
  {
    if (started)
      throw new RuntimeException();
    started = true;
  }

  private DistributedDC(
      final DCOptions options, 
      final DCProposalFactory<P, N> proposalFactory, 
      final DirectedTree<N> tree,
      final DCProcessorFactory<P, N> processorFactory)
  {
    this.options = options;
    this.proposalFactory = proposalFactory;
    this.tree = tree;
    this.processorFactory = processorFactory;
  }
  
  private String createClusterName()
  {
    return "Cluster{" + 
      codeVersion() + 
      "hash(options)=" + HashCodeBuilder.reflectionHashCode(options, DCOptions.INDEX_IN_CLUSTER_FIELD_NAME) + "," +
      "hash(" +proposalFactory.getClass().getName() + ")=" + HashCodeBuilder.reflectionHashCode(proposalFactory) + "}";
  }
  
  private String codeVersion()
  {
    File classFile = RepositoryUtils.findSourceFile(this);
    if (classFile == null) 
      return "";
    VersionControlRepository repository = RepositoryUtils.findRepository(classFile);
    if (repository == null)
      return "";
    return "codeVersion=" + repository.getCommitIdentifier() + ",";
  }

  private Config getConfig()
  {
    Config result = new Config();
    String clusterName = createClusterName();
    System.out.println("Cluster : " + clusterName);
    result.getGroupConfig().setName(clusterName);
    
    // set n threads in executors
    ExecutorConfig ecfg = new ExecutorConfig();
    ecfg.setPoolSize(options.nThreadsPerNode);
    result.addExecutorConfig(ecfg);
    
    // disable map back up
    MapConfig mc = new MapConfig();
    mc.setName(POPULATION_MAP_NAME);
    mc.setBackupCount(0);
    result.addMapConfig(mc);
    
    return result;
  }

  void submitTask(DCRecursionTask<P,N> dcRecursionTask)
  {
    executor.execute(dcRecursionTask);
  }
  
  private static final long sleepTimeMillis = 1000;
  private static void sleep()
  {
    try { Thread.sleep(sleepTimeMillis); }
    catch (Exception e) {}
  }
  
  private static final String POPULATION_MAP_NAME = "POPULATIONS";
}
