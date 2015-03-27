package dc;

import java.util.List;



public interface DCProcessorFactory<P, N> 
{
  public List<DCProcessor<P>> build(DCProcessorFactoryContext<P, N> context);
}