package multilevel;

import java.io.File;
import java.util.Random;

import multilevel.io.MultiLevelDataset;
import multilevel.smc.DivideConquerMCAlgorithm;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelDcSmcOptions;

import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;



public class MultiLevelMain implements Runnable
{
  @Option(required = true) public File inputData;
  @OptionSet(name = "dc") public MultiLevelDcSmcOptions dcsmcOption = new MultiLevelDcSmcOptions();
  @Option public Random random = new Random(1);
  @Option public SamplingMethod samplingMethod = SamplingMethod.DC;
  
  public static enum SamplingMethod { DC, STD }

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
    else
    {
      System.out.println("Starting standard sampling");
      smc.standardSMC_sample(random);
    }
  }

}
