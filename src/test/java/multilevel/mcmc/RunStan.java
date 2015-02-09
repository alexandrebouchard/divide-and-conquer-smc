package multilevel.mcmc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import multilevel.MultiLevelMain;
import multilevel.Node;
import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelBMTreeFactor.Initialization;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import au.com.bytecode.opencsv.CSVParser;
import bayonet.rplot.PlotHistogram;
import binc.Command;
import briefj.BriefIO;
import briefj.opt.Option;
import briefj.opt.OptionSet;
import briefj.run.Mains;
import briefj.run.Results;



public class RunStan implements Runnable
{
  @Option(required = true) public File stanDataFolder;
  @Option public int nIteration = 10000;
  @Option public double burnInFraction = 0.2;
  
  @OptionSet(name = "model") 
  public MultiLevelModelOptions options = new MultiLevelModelOptions();
  
  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new RunStan());
  }

  @Override
  public void run()
  {
    if (options.useTransform == false || options.useUniformVariance || options.variancePriorRateIfExponential != 1.0)
      throw new RuntimeException();
    
    File data = new File(stanDataFolder, GenerateStanModel.DATA_FILE_NAME);
    File executable = new File(stanDataFolder, "model");
    
    System.out.println(executable);
    
    int nSamples = (int) ((1.0 - burnInFraction) * nIteration);
    int nWarmUp = (int) (burnInFraction * nIteration);
    
    Command.byPath(executable)
      .ranIn(Results.getResultFolder())
      .withStandardOutMirroring()
      .withArgs("sample "
          + "num_samples=" + nSamples + " "
          + "num_warmup=" + nWarmUp + " "
          + "data file=" + data.getAbsolutePath())
      .call();
    
    File output = Results.getFileInResultFolder("output.csv");
    
    Predicate<String> commentDetector = ((String line) -> !line.isEmpty() && line.charAt(0) != '#');
    
    // find header line
    @SuppressWarnings("unchecked")
    final List<String> header = Iterables.getFirst(
        BriefIO.readLines(output)
          .filter(commentDetector)
          .transform(BriefIO.splitCSV(new CSVParser())), 
        Collections.EMPTY_LIST);
    
    File originalData = new File(stanDataFolder, "executionInfo/inputs/inputData");
    List<Double> samples = new ArrayList<>();
    MultiLevelDataset dataset = new MultiLevelDataset(originalData);
    for (Map<String,String> parsedLine : 
      BriefIO.readLines(output)
        .filter(commentDetector)
        .skip(1)
        .transform(BriefIO.splitCSV(new CSVParser()))
        .transform(BriefIO.listToMap(header)))
    {
      Adaptor adaptor = new Adaptor(parsedLine);
      
      MultiLevelModel model = new MultiLevelModel(dataset , options , adaptor);
      double curLogDensity = model.multiLevelBMTreeFactor.logDensity();
      samples.add(curLogDensity);
    }
    MultiLevelMain.printMeanDensityStats(samples);
  }
  
  public static class Adaptor implements Initialization
  {
    private final Map<String,String> stanSample;
    
    public Adaptor(Map<String, String> stanSample)
    {
      this.stanSample = stanSample;
    }

    @Override
    public double getLeaf(Node n)
    {
      return Double.parseDouble(stanSample.get(n.toString().replace('-', '_')));
    }

    @Override
    public double getVariance(Node n)
    {
      return Double.parseDouble(stanSample.get("var_" + n.toString()));
    }


    
  }
}
