package multilevel;

import java.io.File;
import java.util.Random;

import multilevel.MultiLevelDcSmc.MultiLevelDcSmcOptions;

import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;



public class MultiLevelMain implements Runnable
{
  @Option public File inputData = 
//    new File("data/small.csv"); 
     new File("data/processed/preprocessedNYSData.csv");
  @OptionSet(name = "dc") public MultiLevelDcSmcOptions dcsmcOption = new MultiLevelDcSmcOptions();
  @Option public Random random = new Random(1);

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
    MultiLevelDcSmc smc = new MultiLevelDcSmc(dataset, dcsmcOption);
    smc.sample(random);
  }

}
