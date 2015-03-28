package dc;




public interface DCProcessorFactory<P, N> 
{
  public DCProcessor<P> build(DCProcessorFactoryContext<P, N> context);
}