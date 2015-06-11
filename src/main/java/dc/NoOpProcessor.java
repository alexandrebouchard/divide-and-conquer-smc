package dc;

import java.util.List;

import bayonet.smc.ParticlePopulation;



public class NoOpProcessor<P> implements DCProcessor<P>
{
  @Override
  public void process(ParticlePopulation<P> populationBeforeResampling,
      List<ParticlePopulation<P>> childrenPopulations)
  {
  }

}
