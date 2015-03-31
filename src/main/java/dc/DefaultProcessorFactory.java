package dc;

import java.util.List;

import org.apache.commons.lang3.time.StopWatch;

import briefj.BriefIO;
import briefj.OutputManager;
import briefj.run.Results;




public class DefaultProcessorFactory<P, N> implements DCProcessorFactory<P, N>
{
  private final OutputManager output;
  private final StopWatch globalTime;
  
  DefaultProcessorFactory()
  {
    output = new OutputManager();
    output.setOutputFolder(Results.getResultFolder());
    globalTime = new StopWatch();
  }

  @Override
  public DCProcessor<P> build(final DCProcessorFactoryContext<P, N> context)
  {
    if (!globalTime.isStarted())
      globalTime.start();
    
    StopWatch timer = new StopWatch();
    timer.start();
    return new DCProcessor<P>() {
      @Override
      public void process(
          ParticlePopulation<P> populationBeforeResampling,
          List<ParticlePopulation<P>> childrenPopulations)
      {
        final int currentNWorkers = context.dc.cluster.getCluster().getMembers().size();
        final long iterationTime = timer.getTime();
        output.printWrite("timing", 
            "node", context.currentNode,
            "ESS", populationBeforeResampling.getESS(),
            "rESS", populationBeforeResampling.getRelativeESS(),
            "logZ", populationBeforeResampling.logNormEstimate(),
            "nWorkers", currentNWorkers, 
            "iterationProposalTime", iterationTime, 
            "globalTime", globalTime.getTime());
        output.flush();
        
        if (context.tree().getRoot().equals(context.currentNode))
          BriefIO.write(Results.getFileInResultFolder("logZ"), "" + populationBeforeResampling.logNormEstimate());
      }
    };
  }
  
  public void close()
  {
    BriefIO.write(Results.getFileInResultFolder("workTime"), "" + globalTime.getTime());
  }

}
