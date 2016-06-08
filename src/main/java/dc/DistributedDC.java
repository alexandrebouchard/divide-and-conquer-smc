package dc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.time.StopWatch;

import bayonet.graphs.DirectedTree;
import bayonet.smc.ParticlePopulation;
import briefj.repo.RepositoryUtils;
import briefj.repo.VersionControlRepository;

import com.hazelcast.config.Config;
import com.hazelcast.config.ExecutorConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;



public final class DistributedDC<P, N>
{
  final DCOptions options;
  final DCProposalFactory<P, N> proposalFactory;
  final DirectedTree<N> tree;
  final List<DCProcessorFactory<P, N>> processorFactories = new ArrayList<>();
  
  HazelcastInstance cluster;
  Map<N, ParticlePopulation<P>> populations;
  Map<N, Integer> numberOfUnprocessedChildren;
  Map<String,Boolean> clusterStatus;
  Lock nucLock; // lock for numberOfUnprocessedChildren
  ExecutorService executor;
  
  /*
   * TODO:
   *  - recovery of the task if one of the nodes crashes 
   *  - move towards EntryProcessor to avoid shuffling data? add locality ('near cache?') ?
   */
  
  private boolean started = false;
  private boolean initializing = false;
  
  /*
   * Implementation note: we use a singleton so that multiple threads do not each create copies 
   * of the populations. This has the limitation explained in the exception thrown by 
   * createInstance() (see below).
   */
  @SuppressWarnings("rawtypes")
  private static DistributedDC instance = null;
  
  @SuppressWarnings("unchecked")
  public static <P,N> DistributedDC<P,N> createInstance(
      DCOptions options, 
      DCProposalFactory<P, N> proposalFactory, 
      DirectedTree<N> tree)
  {
    if (instance != null)
      throw new RuntimeException("Limitation: the current implementation supports only one "
          + "distributed DC computation at any given time in one JVM. Note that this DC "
          + "computation can be multi-threaded (see DCOptions), however you could not say "
          + "start two parallel DC computation on different trees. ");
    instance = new DistributedDC<P,N>(options, proposalFactory, tree);
    return instance;
  }
  
  public void addProcessorFactory(DCProcessorFactory<P, N> factory)
  {
    this.processorFactories.add(factory);
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
  
  private ParticlePopulation<P> rootPopulation = null;
  private void monitor()
  {
    while (!populations.containsKey(tree.getRoot()))
      sleep();
    for (DCProcessorFactory<P, N> factory : processorFactories)
      factory.close();
    // for convenience, save the root population locally
    rootPopulation = populations.get(tree.getRoot());
    cluster.shutdown(); // NB: this makes populations.get(.) inactive
    instance = null;
  }
  
  public ParticlePopulation<P> getRootPopulation() 
  {
    return rootPopulation;
  }

  private void setup()
  {
    initHazelcast();
    waitForEnoughWorkers();
    Lock lock = cluster.getLock("SETUP_LOCK");
    lock.lock();
    // if this was not already done by some other node
    if (!clusterStatus.containsKey(SETUP_COMPLETE))
    {
      // setup numberOfUnprocessedChildren
      for (N node : tree.getNodes())
        numberOfUnprocessedChildren.put(node, tree.getChildren(node).size());
      
      // setup basic tasks
      for (DCRecursionTask<P,N> initialTask : initialTasks())
        submitTask(initialTask);
      
      clusterStatus.put(SETUP_COMPLETE, true);
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
    { 
      this.clusterStatus = cluster.getMap("CLUSTER_STATUS");
      this.populations = cluster.getMap(POPULATION_MAP_NAME);
      this.numberOfUnprocessedChildren = cluster.getMap("N_UNPROCESSED");
      this.nucLock = cluster.getLock("NUC_LOCK");
      this.executor = cluster.getExecutorService("EXECUTOR");  
    }
    this.initializing  = false;
  }

  private List<DCRecursionTask<P,N>> initialTasks()
  {
    List<DCRecursionTask<P,N>> result = new ArrayList<>();
    populateInitialTasks(result, tree.getRoot(), options.maximumDistributionDepth);
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
      final DirectedTree<N> tree)
  {
    this.options = options;
    this.proposalFactory = proposalFactory;
    this.tree = tree;
    // add a default processor
    this.processorFactories.add(new DefaultProcessorFactory<P,N>());
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
    executor.submit(dcRecursionTask);
  }
  
  private static final long sleepTimeMillis = 100;
  private static void sleep()
  {
    try { Thread.sleep(sleepTimeMillis); }
    catch (Exception e) {}
  }
  
  private static final String POPULATION_MAP_NAME = "POPULATIONS";
  private static final String SETUP_COMPLETE = "SETUP_COMPLETE";

}
