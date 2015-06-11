package dc;

import java.util.List;

import bayonet.smc.ParticlePopulation;



/**
 * Note: implementation do not need to be thread safe, even when the distributed/parallel version is used.
 * 
 * @author Alexandre Bouchard (alexandre.bouchard@gmail.com)
 *
 * @param <P>
 */
public interface DCProcessor<P>
{
  public void process(
      ParticlePopulation<P> populationBeforeResampling, 
      List<ParticlePopulation<P>> childrenPopulations);
  
}
