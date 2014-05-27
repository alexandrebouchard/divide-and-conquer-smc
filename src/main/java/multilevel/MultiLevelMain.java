package multilevel;

import java.io.File;
import java.util.Random;

import briefj.opt.Option;
import briefj.run.Mains;



public class MultiLevelMain implements Runnable
{
  @Option public File inputData = new File("data/processed/preprocessedNYSData.csv");
  @Option public int nParticles = 100000;
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
    MultiLevelDcSmc smc = new MultiLevelDcSmc(dataset, nParticles);
    smc.sample(random);
  }

}
