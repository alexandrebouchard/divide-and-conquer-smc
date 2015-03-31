package dc;



/**
 * Note: implementation do not need to be thread safe, even when the distributed/parallel version is used.
 * 
 * @author Alexandre Bouchard (alexandre.bouchard@gmail.com)
 *
 * @param <P>
 * @param <N>
 */
public interface DCProcessorFactory<P, N> 
{
  public DCProcessor<P> build(DCProcessorFactoryContext<P, N> context);
  
  /**
   * Called when the DC calculation is completed
   */
  public void close();
}