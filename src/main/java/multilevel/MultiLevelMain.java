  package multilevel;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import com.google.common.collect.Lists;

import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelBMTreeFactor;
import multilevel.mcmc.MultiLevelBMTreeFactor.Initialization;
import multilevel.mcmc.MultiLevelModel;
import multilevel.smc.DivideConquerMCAlgorithm;
import multilevel.smc.DivideConquerMCAlgorithm.LogDensityApprox;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelDcSmcOptions;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import bayonet.coda.CodaParser;
import bayonet.coda.SimpleCodaPlots;
import blang.MCMCAlgorithm;
import blang.MCMCFactory;
import blang.mcmc.RealVariableMHProposal;
import blang.mcmc.RealVariablePeskunTypeMove;
import blang.processing.LogDensityProcessor;
import blang.variables.RealVariable;
import blang.variables.RealVariableProcessor;
import briefj.OutputManager;
import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;
import briefj.run.Results;



public class MultiLevelMain implements Runnable
{
  @Option(required = true) public File inputData;
  @OptionSet(name = "dc") public MultiLevelDcSmcOptions dcsmcOption = new MultiLevelDcSmcOptions();
  @OptionSet(name = "model") public MultiLevelModelOptions modelOptions = new MultiLevelModelOptions();
  @Option public Random mainRandom = new Random(1);
  @Option public SamplingMethod samplingMethod = SamplingMethod.DC;
  
  @Option public boolean initGibbsWithStdSMC = false;
  
  @OptionSet(name = "factory")
  public final MCMCFactory factory = new MCMCFactory();
  
  public static enum SamplingMethod { DC, STD, GIBBS }

  /**
   * @param args
   */
  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new MultiLevelMain());
  }
  
  @Override
  public void run()
  {
    MultiLevelDataset dataset = new MultiLevelDataset(inputData);
    DivideConquerMCAlgorithm smc = new DivideConquerMCAlgorithm(dataset, dcsmcOption, modelOptions);
    
    Initialization init = null;
    if (initGibbsWithStdSMC && samplingMethod == SamplingMethod.GIBBS)
    {
      Map<Node, Particle> standardSMC_sample = smc.standardSMC_sample(mainRandom).sample(mainRandom);
      init = new MultiLevelBMTreeFactor.InitFromSMC(standardSMC_sample);
    }
    
    LogDensityApprox approx = null;
    if (samplingMethod == SamplingMethod.DC)  
    {
      System.out.println("Starting DC sampling");
      approx = smc.dc_sample(mainRandom);
    }
    else if (samplingMethod == SamplingMethod.STD)
    {
      System.out.println("Starting standard SMC sampling");
      approx = smc.standardSMC_sample(mainRandom);
    }
    else if (samplingMethod == SamplingMethod.GIBBS)
    {      
      System.out.println("Starting GIBBS sampling");
      MultiLevelModel modelSpec = new MultiLevelModel(dataset, modelOptions, init);
      factory.excludeNodeMove(RealVariablePeskunTypeMove.class);
      factory.addNodeMove(RealVariable.class, RealVariableMHProposal.class);
      factory.addProcessor(new LogDensityProcessor());
      factory.excludeNodeProcessor(RealVariableProcessor.class);
      MCMCAlgorithm mcmc = factory.build(modelSpec, false);
      mcmc.run();
    }
    else
      throw new RuntimeException();
    
    if (approx != null)
    {
      List<Double> numbers = Lists.newArrayList();
      for (int i = 0; i < Math.min(1000, dcsmcOption.nParticles); i++)
        numbers.add(approx.sampleNextLogDensity(mainRandom));
      printMeanDensityStats(numbers);
    }
  }
  
  public static void printMeanDensityStats(List<Double> samples)
  {
    SummaryStatistics statistics = new SummaryStatistics();
    for (double sample : samples)
      statistics.addValue(sample);
    String statString = statistics.toString();
    String[] sepStats = statString.split("\n");
    String[] toWrite = null; 
    for (int i = 1; i < sepStats.length; i++) // avoid the first element; serves as an unneeded header
    {
      String statSplit = sepStats[i];
      String[] stat = statSplit.split(":");
      toWrite = ArrayUtils.addAll(toWrite, stat);
    }
    OutputManager output = new OutputManager();
    output.setOutputFolder(Results.getResultFolder());
    output.printWrite("logDensity-summary", (Object []) toWrite);
    output.close();
    File loglDensityDir = Results.getFileInResultFolder("logDensity");
    File codaIndex = new File(loglDensityDir, "codaIndex");
    File codaContents=new File(loglDensityDir, "codaContents");
    CodaParser.listToCoda(codaIndex, codaContents, samples, "logDensity");
    SimpleCodaPlots simpleCodaPlots = new SimpleCodaPlots(codaContents, codaIndex);
    simpleCodaPlots.toPDF(new File(loglDensityDir, "plot.pdf"));
  }
}
