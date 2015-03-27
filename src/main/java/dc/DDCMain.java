package dc;

import multilevel.adaptor.MultiLevelProposalFactory;
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
    DistributedDC.createInstance(options, proposalFactory, proposalFactory.getDataset().getTree()).start();
  }

  public static void main(String [] args)
  {
    Mains.instrumentedRun(args, new DDCMain());
  }
}
