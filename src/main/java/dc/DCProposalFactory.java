package dc;

import java.util.List;
import java.util.Random;

import tutorialj.Tutorial;


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
  @Tutorial(showSignature = true, showLink = true)
  /**
   * 
   * @param random
   * @param currentNode
   * @param childrenNodes
   * @return
   */
  public DCProposal<P> build(Random random, N currentNode, List<N> childrenNodes);
}