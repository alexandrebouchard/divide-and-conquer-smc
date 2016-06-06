package prototype.adaptor;


import prototype.Node;
import prototype.smc.DivideConquerMCAlgorithm.Particle;
import dc.DCProcessor;
import dc.DCProcessorFactory;
import dc.DCProcessorFactoryContext;
import dc.NoOpProcessor;



public final class MultiLevelProcessorFactory implements DCProcessorFactory<Particle,Node>
{

  @Override
  public DCProcessor<Particle> build(DCProcessorFactoryContext<Particle, Node> context)
  {
    // TODO: graphs, etc
    return new NoOpProcessor<>();
  }

  @Override
  public void close()
  {
  }

}
