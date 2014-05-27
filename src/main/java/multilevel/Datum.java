package multilevel;



public class Datum
{
  public final int numberOfTrials, numberOfSuccesses;

  Datum(int numberOfTrials, int numberOfSuccesses)
  {
    this.numberOfTrials = numberOfTrials;
    this.numberOfSuccesses = numberOfSuccesses;
  }
}