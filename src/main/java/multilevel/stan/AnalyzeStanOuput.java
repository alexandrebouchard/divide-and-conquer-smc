package multilevel.stan;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import multilevel.MultiLevelMain;
import multilevel.Node;
import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelModel;
import multilevel.mcmc.MultiLevelBMTreeFactor.Initialization;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;
import au.com.bytecode.opencsv.CSVParser;
import bayonet.rplot.PlotHistogram;
import briefj.BriefIO;
import briefj.opt.InputFile;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;

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
    
    Map<String,List<Double>> marginals = new HashMap<>();
    for (Node node : dataset.postOrder())
      if (node.level() < 2)
      {
        marginals.put("var_" + node2stan(node), new ArrayList<>());
        marginals.put(node2stan(node), new ArrayList<>());
      }
    
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
      
      for (String key : marginals.keySet())
        marginals.get(key).add(Double.parseDouble(parsedLine.get(key)));
    }
    MultiLevelMain.printMeanDensityStats(samples);
    
    File histogramsDir = Results.getFolderInResultFolder("histograms");
    
    for (String key : marginals.keySet())
    {
      List<Double> marginalSamples = marginals.get(key);
      PlotHistogram.from(marginalSamples).toPDF(new File(histogramsDir, key + ".pdf"));
    }
  }
  
  private static String node2stan(Node node)
  {
    return node.toString().replace('-', '_');
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
      return Double.parseDouble(stanSample.get(node2stan(n)));
    }

    @Override
    public double getVariance(Node n)
    {
      return Double.parseDouble(stanSample.get("var_" + node2stan(n)));
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
