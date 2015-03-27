package multilevel.adaptor;

import java.util.List;

import briefj.BriefIO;
import briefj.run.Results;
import multilevel.Node;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import dc.DCProcessor;
import dc.ParticlePopulation;



public final class MultiLevelProcessor implements DCProcessor<Particle>
{
  private final Node node;
  private final boolean isRoot;
  
  MultiLevelProcessor(Node node, boolean isRoot)
  {
    this.node = node;
    this.isRoot = isRoot;
  }

  @Override
  public void process(
      ParticlePopulation<Particle> populationBeforeResampling,
      List<ParticlePopulation<Particle>> childrenPopulations)
  {
    System.out.println("relESS @ " + node + " = " + populationBeforeResampling.getRelativeESS());
    if (isRoot)
      BriefIO.write(Results.getFileInResultFolder("logZ"), "" + populationBeforeResampling.logNormEstimate());
  }
  
}