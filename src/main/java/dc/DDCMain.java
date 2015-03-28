package dc;

import multilevel.Node;
import multilevel.adaptor.MultiLevelProcessorFactory;
import multilevel.adaptor.MultiLevelProposalFactory;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import briefj.opt.OptionSet;
import briefj.run.Mains;



public class DDCMain implements Runnable
{
  @OptionSet(name = "dc")
  public DCOptions options = new DCOptions();
  
  @OptionSet(name = "multiLevel")
  public MultiLevelProposalFactory proposalFactory = new MultiLevelProposalFactory();
  
  @Override
  public void run()
  {
    DistributedDC<Particle, Node> instance = DistributedDC
      .createInstance(
          options, 
          proposalFactory, 
          proposalFactory.getDataset().getTree());
    instance.addProcessorFactory(new MultiLevelProcessorFactory());
    instance.start();
  }

  public static void main(String [] args)
  {
    Mains.instrumentedRun(args, new DDCMain());
  }
}
