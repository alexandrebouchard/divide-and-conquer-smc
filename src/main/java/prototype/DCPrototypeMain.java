  package prototype;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import prototype.io.MultiLevelDataset;
import prototype.mcmc.MultiLevelBMTreeFactor;
import prototype.mcmc.MultiLevelModel;
import prototype.mcmc.MultiLevelBMTreeFactor.Initialization;
import prototype.smc.DivideConquerMCAlgorithm;
import prototype.smc.DivideConquerMCAlgorithm.LogDensityApprox;
import prototype.smc.DivideConquerMCAlgorithm.DcSmcOptions;
import prototype.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;
import prototype.smc.DivideConquerMCAlgorithm.Particle;
import bayonet.coda.CodaParser;
import bayonet.coda.SimpleCodaPlots;
import blang.MCMCAlgorithm;
import blang.MCMCFactory;
import blang.mcmc.RealVariableMHProposal;
import blang.mcmc.RealVariablePeskunTypeMove;
import blang.processing.LogDensityProcessor;
import blang.processing.Processor;
import blang.processing.ProcessorContext;
import blang.variables.RealVariable;
import blang.variables.RealVariableProcessor;
import briefj.OutputManager;
import briefj.opt.InputFile;
import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;
import briefj.run.Results;

import com.google.common.collect.Lists;



public class DCPrototypeMain implements Runnable
{
  @InputFile
  @Option(required = true) 
  public File inputData;
  
  @OptionSet(name = "dc") 
  public DcSmcOptions dcsmcOption = new DcSmcOptions();
  
  @OptionSet(name = "model") 
  public MultiLevelModelOptions modelOptions = new MultiLevelModelOptions();
  
  @Option 
  public Random mainRandom = new Random(1);
  
  @Option 
  public SamplingMethod samplingMethod = SamplingMethod.DC;
  
  @Option 
  public boolean initGibbsWithStdSMC = false;
  
  @OptionSet(name = "factory")
  public final MCMCFactory factory = new MCMCFactory();
  
  @Option
  public int indexInCluster = 1;
  
  @Option
  public int nThreadsPerNode = 1;
  
  public static enum SamplingMethod { DC, STD, GIBBS }
  
  public OutputManager output = new OutputManager();

  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new DCPrototypeMain());
  }
  
  @Override
  public void run()
  {
    MultiLevelDataset dataset = new MultiLevelDataset(inputData);
    DivideConquerMCAlgorithm smc = new DivideConquerMCAlgorithm(dataset, dcsmcOption, modelOptions);
    output.setOutputFolder(Results.getResultFolder());
    
    Initialization init = null;
    if (initGibbsWithStdSMC && samplingMethod == SamplingMethod.GIBBS)
    {
      Map<Node, Particle> standardSMC_sample = smc.standardSMC_sample(mainRandom, true).sample(mainRandom);
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
      approx = smc.standardSMC_sample(mainRandom, false);
    }
    else if (samplingMethod == SamplingMethod.GIBBS)
    {      
      System.out.println("Starting GIBBS sampling");
      MultiLevelModel modelSpec = new MultiLevelModel(dataset, modelOptions, init);
      factory.excludeNodeMove(RealVariablePeskunTypeMove.class);
      factory.addNodeMove(RealVariable.class, RealVariableMHProposal.class);
      factory.addProcessor(new LogDensityProcessor());
      factory.addProcessor(new TopSampleProcessor());
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
  
  public class TopSampleProcessor implements Processor
  {
    @Override
    public void process(ProcessorContext context)
    {
      ((MultiLevelBMTreeFactor) context.getModel().linearizedFactors().get(0)).logSamples(2, output, context.getMcmcIteration());
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
