package multilevel.mcmc;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import multilevel.Node;
import multilevel.io.MultiLevelDataset;
import bayonet.rplot.PlotHistogram;
import binc.Command;
import briefj.BriefIO;
import briefj.BriefMaps;
import briefj.OutputManager;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;



public class ReadStanOutput implements Runnable
{
  @Option public File inputData = new File("data/preprocessedNYSData.csv");
  @Option public String stanHome = "/Users/bouchard/Documents/workspace-cpp/cmdstan";
  @Option public File stanOutput = new File("results/all/2014-08-05-06-07-25-M7STyI34.exec/output-400.csv");
  
  @Option public File smcExec = new File("/Users/bouchard/Documents/experiments/logitree/nipsFinalPlots/2014-05-28--22-20-47/1000");

  private MultiLevelDataset dataset;
  private int maxLevel = 1;
  private Set<String> nodesToPlot = Sets.newLinkedHashSet();
  
  /**
   * @param args
   */
  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new ReadStanOutput());
  }
  
  

  @Override
  public void run()
  {
    dataset = new MultiLevelDataset(inputData);
    
    // read names of top level elmts
    readTopElements(dataset.getRoot(), 0);
    
    // find smc ess stats
    double timeSec = timeSec();
//    System.out.println(timeSec);
    Map<String,Double> smcEssPerSec = Maps.newLinkedHashMap();
    for (Map<String,String> fields : BriefIO.readLines(new File(smcExec, "ess.csv")).indexCSV())
    {
      String nodeName = fields.get("nodeLabel").replaceAll("[-]", "_");
      if (nodesToPlot.contains(nodeName))
      {
        double ess = Double.parseDouble(fields.get("ess"));
        double essPerSec = ess/timeSec;
        smcEssPerSec.put(nodeName, essPerSec);
      }
    }
    
    // print stats
    Command printStan = Command.byPath(new File(stanHome, "bin/print"));
    String result = Command.call(printStan.withArg(stanOutput.getAbsolutePath()));
    File stanOutEss = Results.getFileInResultFolder("stan-ess.csv");
    BriefIO.write(stanOutEss, result);
    OutputManager output = new OutputManager();
    output.setOutputFolder(Results.getResultFolder());
    for (String line : result.split("\n"))
    {
      String [] fields = line.split("\\s+");
      if (fields.length > 0 && nodesToPlot.contains(fields[0]))
      {
        double essPerSec = Double.parseDouble(fields[8]);
        String var = fields[0];
        if (smcEssPerSec.keySet().contains(var))
          output.printWrite("ess-comparison", "node", var, "stanEssPerSec", essPerSec, "smcEssPerSec", smcEssPerSec.get(var), "speedup", "" + (smcEssPerSec.get(var)/essPerSec));
      }
    }
    output.flush();
    
    // plot stuff
    
    Map<String,Integer> indices = null;
    Map<String,List<Double>> data = Maps.newLinkedHashMap();
    
    loop:for (List<String> line : BriefIO.readLines(stanOutput).filter(Predicates.not(Predicates.containsPattern("[#].*"))).transform(BriefIO.splitCSV))
    {
      if (line.isEmpty())
        continue loop;
      if (line.get(0).equals("lp__"))
        indices = buildIndices(line);
      else
      {
        for (String nodeName : nodesToPlot)
          try
          {
            int index = indices.get(nodeName);
            String datumStr = line.get(index);
            BriefMaps.getOrPutList(data, nodeName).add(Double.parseDouble(datumStr));
          }
          catch (Exception e)
          {
//            System.err.println(e);
          }
      }
    }
    for (String nodeName : nodesToPlot)
    {
      PlotHistogram.from(data.get(nodeName)).toPDF(Results.getFileInResultFolder(nodeName + ".pdf"));
    }
  }

  private double timeSec()
  {
    long startTime = Long.parseLong(BriefIO.fileToString(new File(smcExec, "executionInfo/start-time.txt")));
    long endTime   = Long.parseLong(BriefIO.fileToString(new File(smcExec, "executionInfo/end-time.txt")));
    return (endTime - startTime) / 1000.0;
  }



  private Map<String, Integer> buildIndices(List<String> line)
  {
    Map<String, Integer> result = Maps.newLinkedHashMap();
    for (String nodeName : nodesToPlot)
      result.put(nodeName, line.indexOf(nodeName));
    return result;
  }

  private void readTopElements(Node node, int level)
  {
    if (level > maxLevel)
      return;
    
    nodesToPlot.add(GenerateStanModel.thetaNodeName(node));
    
    if (!dataset.getChildren(node).isEmpty())
      nodesToPlot.add(GenerateStanModel.varianceNodeName(node));
    
    for (Node child : dataset.getChildren(node))
      readTopElements(child, level + 1);
  }
}
