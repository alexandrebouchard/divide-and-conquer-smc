package dc;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;


/**
 * Note: implementation do not need to be thread safe, even when the distributed/parallel version is used.
 * 
 * @author Alexandre Bouchard (alexandre.bouchard@gmail.com)
 *
 * @param <P>
 */
public interface DCProposal<P>
{
  public Pair<Double, P> propose(Random random, List<P> childrenParticles);
}
