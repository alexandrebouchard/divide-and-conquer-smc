package dc;

import java.util.List;




public interface DCProcessor<P>
{
  public void process(ParticlePopulation<P> populationBeforeResampling, List<ParticlePopulation<P>> childrenPopulations);
}
