package multilevel.adaptor;

import java.util.Collections;
import java.util.List;

import multilevel.Node;
import multilevel.smc.DivideConquerMCAlgorithm.Particle;
import dc.DCProcessor;
import dc.DCProcessorFactory;
import dc.DCProcessorFactoryContext;



public final class MultiLevelProcessorFactory implements DCProcessorFactory<Particle,Node>
{

  @Override
  public List<DCProcessor<Particle>> build(DCProcessorFactoryContext<Particle, Node> context)
  {
    if (context.tree.getChildren(context.currentNode).isEmpty())
      return Collections.emptyList();
    else
      return Collections.singletonList(new MultiLevelProcessor(context.currentNode, context.tree.getRoot().equals(context.currentNode)));
  }

}
