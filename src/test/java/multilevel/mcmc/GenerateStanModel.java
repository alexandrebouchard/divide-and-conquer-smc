package multilevel.mcmc;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import multilevel.Node;
import multilevel.io.MultiLevelDataset;
import binc.Command;
import binc.Commands;
import briefj.BriefIO;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;



public class GenerateStanModel implements Runnable
{
  @Option(required = true) public File inputData;
  private MultiLevelDataset dataset;
  @Option public String stanHome = "/Users/bouchard/Documents/workspace-cpp/cmdstan";

  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new GenerateStanModel());
  }

  @Override
  public void run()
  {
    dataset = new MultiLevelDataset(inputData);
    readData(dataset.getRoot(), null);
    File modelFile = createModelFile();
    createDataFile();
    compileModel(modelFile);
  }
  
  
  private void compileModel(File modelFile)
  {
    String arg = modelFile.getAbsolutePath().replaceAll("[.]stan$", "");
    Command.call(Commands.make.ranIn(new File(stanHome)).withArg(arg).withStandardOutMirroring());
  }

  private void createDataFile()
  {
    File dataFile = Results.getFileInResultFolder("data.R");
    PrintWriter out = BriefIO.output(dataFile);
    for (String line : data)
      out.println(line);
    out.close();
  }

  private File createModelFile()
  {
    File modelFile = Results.getFileInResultFolder("model.stan");
    PrintWriter out = BriefIO.output(modelFile);
    out.println("data {");
    for (String line : dataDeclaration)
      out.println("  " + line);
    out.println("}");
    out.println("parameters {");
    for (String line : parametersDeclarations)
      out.println("  " + line);
    out.println("}");
    out.println("model {");
    for (String line : distributions)
      out.println("  " + line);
    out.println("}");
    out.close();
    return modelFile;
  }

  private List<String> distributions = Lists.newArrayList();
  private Set<String> parametersDeclarations = Sets.newLinkedHashSet();
  private Set<String> dataDeclaration = Sets.newLinkedHashSet();
  private List<String> data = Lists.newArrayList();
  
  public static String varianceNodeName(Node node)
  {
    return "var_" + node.toString("_");
  }
  
  public static String thetaNodeName(Node node)
  {
    return node.toString("_");
  }

  private void readData(Node node, Node parent)
  {
    if (parent != null)
    {
      String varianceVarName = varianceNodeName(parent);
      parametersDeclarations.add("real<lower=0> " + varianceVarName + ";");
      parametersDeclarations.add("real " + thetaNodeName(node) + ";");
      parametersDeclarations.add("real " + thetaNodeName(parent) + ";");
      distributions.add(varianceVarName + " ~ exponential(1);");
      distributions.add("" + thetaNodeName(node) + " ~ " + "normal(" + thetaNodeName(parent) + ", " + varianceVarName + ");");
    }
    
    if (dataset.getChildren(node).isEmpty())
    {
      String numberOfSuccessVar = node.toString("_") + "_numberOfSuccesses";
      String numberOfTrialsVar = node.toString("_") + "_numberOfTrials";
      dataDeclaration.add("int<lower=0> " + numberOfSuccessVar + ";");
      dataDeclaration.add("int<lower=0> " + numberOfTrialsVar + ";");
      distributions.add("" + numberOfSuccessVar + " ~ binomial_logit(" + numberOfTrialsVar + ", " + thetaNodeName(node) + ");");
      data.add(numberOfSuccessVar + " <- " + dataset.getDatum(node).numberOfSuccesses);
      data.add(numberOfTrialsVar + " <- " + dataset.getDatum(node).numberOfTrials);
    }
    else
      for (Node child : dataset.getChildren(node))
        readData(child, node);
  }
}
