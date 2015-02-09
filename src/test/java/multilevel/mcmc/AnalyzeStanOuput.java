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
import au.com.bytecode.opencsv.CSVParser;
import briefj.BriefIO;
import briefj.opt.InputFile;
import briefj.opt.Option;
import briefj.run.Mains;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;



public class AnalyzeStanOuput implements Runnable
{
  @InputFile
  @Option(required = true) 
  public File inputData;
  
  @InputFile
  @Option(required = true)
  public File stanOutput;
  
  public static void analyzeStanOutput(MultiLevelDataset dataset, File stanOutput)
  {
    Predicate<String> commentDetector = ((String line) -> !line.isEmpty() && line.charAt(0) != '#');
    MultiLevelModelOptions options = new MultiLevelModelOptions();
    // find header line
    @SuppressWarnings("unchecked")
    final List<String> header = Iterables.getFirst(
        BriefIO.readLines(stanOutput)
          .filter(commentDetector)
          .transform(BriefIO.splitCSV(new CSVParser())), 
        Collections.EMPTY_LIST);
    
    List<Double> samples = new ArrayList<>();
    for (Map<String,String> parsedLine : 
      BriefIO.readLines(stanOutput)
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

  @Override
  public void run()
  {
    MultiLevelDataset dataset = new MultiLevelDataset(inputData);
    analyzeStanOutput(dataset, stanOutput);
  }
  
  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new AnalyzeStanOuput());
  }
}
