package dc;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;



public interface DCProposal<P, N>
{
  public Pair<Double, P> propose(Random random, List<P> childrenParticles);
}
