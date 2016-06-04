package dc;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;

import tutorialj.Tutorial;


/**
 * Note: implementation do not need to be thread safe, even when the distributed/parallel version is used.
 * 
 * @author Alexandre Bouchard (alexandre.bouchard@gmail.com)
 *
 * @param <P>
 */
@FunctionalInterface
public interface DCProposal<P>
{
  @Tutorial(showSignature = true, showLink = true)
  /**
   * Propose a parent particle given the children. 
   * All the randomness should be obtained via the provided random object.
   * 
   * @param random
   * @param childrenParticles
   * @return A pair containing the LOG weight update and the proposed particle
   */
  public Pair<Double, P> propose(Random random, List<P> childrenParticles);
}
