package multilevel;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.Lists;

import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelModel;
import multilevel.smc.DivideConquerMCAlgorithm;
import multilevel.smc.DivideConquerMCAlgorithm.LogDensityApprox;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelDcSmcOptions;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import bayonet.coda.CodaParser;
import bayonet.coda.SimpleCodaPlots;
import blang.MCMCAlgorithm;
import blang.MCMCFactory;
import blang.processing.LogDensityProcessor;
import blang.processing.Processor;
import blang.processing.ProcessorContext;
import blang.variables.RealVariableProcessor;
import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;
import briefj.run.Results;



public class MultiLevelMain implements Runnable, Processor
{
  @Option(required = true) public File inputData;
  @OptionSet(name = "dc") public MultiLevelDcSmcOptions dcsmcOption = new MultiLevelDcSmcOptions();
  @Option public Random random = new Random(1);
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
  
  public static Map<Node, Particle> standardSMC_sample;

  @Override
  public void run()
  {
    MultiLevelDataset dataset = new MultiLevelDataset(inputData);
    DivideConquerMCAlgorithm smc = new DivideConquerMCAlgorithm(dataset, dcsmcOption);
    
    if (initGibbsWithStdSMC && samplingMethod == SamplingMethod.GIBBS)
      standardSMC_sample = smc.standardSMC_sample(random).sample(random);
    
    LogDensityApprox approx = null;
    if (samplingMethod == SamplingMethod.DC)  
    {
      System.out.println("Starting DC sampling");
      approx = smc.dc_sample(random);
    }
    else if (samplingMethod == SamplingMethod.STD)
    {
      System.out.println("Starting standard sampling");
      approx = smc.standardSMC_sample(random);
    }
    else if (samplingMethod == SamplingMethod.GIBBS)
    {
      MultiLevelModel modelSpec = new MultiLevelModel(dataset);
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
        numbers.add(approx.sampleNextLogDensity(random));
      File loglDensityDir = Results.getFileInResultFolder("logDensity");
      File codaIndex = new File(loglDensityDir, "codaIndex");
      File codaContents=new File(loglDensityDir, "codaContents");
      CodaParser.listToCoda(codaIndex, codaContents, numbers, "logDensity");
      SimpleCodaPlots simpleCodaPlots = new SimpleCodaPlots(codaContents, codaIndex);
      simpleCodaPlots.toPDF(new File(loglDensityDir, "plot.pdf"));
    }
  }

  
  @Override
  public void process(ProcessorContext context)
  {
    System.out.println(context.getModel().logDensity());
  }

}
