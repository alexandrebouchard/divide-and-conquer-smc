package dc;

import java.util.List;
import java.util.Random;


/**
 * Note: implementation do not need to be thread safe, even when the distributed/parallel version is used.
 * 
 * @author Alexandre Bouchard (alexandre.bouchard@gmail.com)
 *
 * @param <P>
 * @param <N>
 */
public interface DCProposalFactory<P, N> 
{
  public DCProposal<P> build(Random random, N currentNode, List<N> childrenNodes);
}