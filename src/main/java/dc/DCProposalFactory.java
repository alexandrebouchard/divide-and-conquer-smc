package dc;

import java.util.List;
import java.util.Random;



public interface DCProposalFactory<P, N> 
{
  public DCProposal<P, N> build(Random random, N currentNode, List<N> childrenNodes);
}