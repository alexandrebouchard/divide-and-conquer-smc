package multilevel;

import java.io.File;
import java.util.Random;

import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelModel;
import multilevel.smc.DivideConquerMCAlgorithm;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelDcSmcOptions;
import blang.MCMCAlgorithm;
import blang.MCMCFactory;
import blang.processing.Processor;
import blang.processing.ProcessorContext;
import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;



public class MultiLevelMain implements Runnable, Processor
{
  @Option(required = true) public File inputData;
  @OptionSet(name = "dc") public MultiLevelDcSmcOptions dcsmcOption = new MultiLevelDcSmcOptions();
  @Option public Random random = new Random(1);
  @Option public SamplingMethod samplingMethod = SamplingMethod.DC;
  
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
    DivideConquerMCAlgorithm smc = new DivideConquerMCAlgorithm(dataset, dcsmcOption);
    
    if (samplingMethod == SamplingMethod.DC)  
    {
      System.out.println("Starting DC sampling");
      smc.dc_sample(random);
    }
    else if (samplingMethod == SamplingMethod.STD)
    {
      System.out.println("Starting standard sampling");
      smc.standardSMC_sample(random);
    }
    else if (samplingMethod == SamplingMethod.GIBBS)
    {
      MultiLevelModel modelSpec = new MultiLevelModel(dataset);
      factory.addProcessor(this);
      MCMCAlgorithm mcmc = factory.build(modelSpec, false);
      mcmc.run();
    }
    else
      throw new RuntimeException();
  }

  @Override
  public void process(ProcessorContext context)
  {
    System.out.println(context.getModel().logDensity());
  }

}
