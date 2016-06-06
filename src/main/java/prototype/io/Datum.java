package prototype.io;



public class Datum
{
  public final int numberOfTrials, numberOfSuccesses;

  public Datum(int numberOfTrials, int numberOfSuccesses)
  {
    this.numberOfTrials = numberOfTrials;
    this.numberOfSuccesses = numberOfSuccesses;
  }

  @Override
  public String toString()
  {
    return "Datum [numberOfTrials=" + numberOfTrials + ", numberOfSuccesses="
        + numberOfSuccesses + "]";
  }
  
  
}