package multilevel.stan;

import java.io.File;

import multilevel.io.MultiLevelDataset;
import binc.Command;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;



public class RunStan implements Runnable
{
  @Option(required = true) public File stanDataFolder;
  @Option public int nIteration = 10000;
  @Option public double burnInFraction = 0.2;
  @Option public int thin = 10;
  @Option public int seed = 1;
  
  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new RunStan());
  }

  @Override
  public void run()
  {
    File data = new File(stanDataFolder, GenerateStanModel.DATA_FILE_NAME);
    File executable = new File(stanDataFolder, "model");
    
    System.out.println(executable);
    
    int nSamples = (int) ((1.0 - burnInFraction) * nIteration);
    int nWarmUp = (int) (burnInFraction * nIteration);
    
    Command.byPath(executable)
      .ranIn(Results.getResultFolder())
      .withStandardOutMirroring()
      .withArgs("sample "
          + "thin=" + thin  + " "
          + "num_samples=" + nSamples + " "
          + "num_warmup=" + nWarmUp + " "
          + "random seed=" + seed  + " "
          + "data file=" + data.getAbsolutePath())
      .call();
    
    File output = Results.getFileInResultFolder("output.csv");
    File originalData = new File(stanDataFolder, "executionInfo/inputs/inputData");
    MultiLevelDataset dataset = new MultiLevelDataset(originalData);
    AnalyzeStanOuput.analyzeStanOutput(dataset, output);
  }
}
