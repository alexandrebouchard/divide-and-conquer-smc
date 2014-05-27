package multilevel;



public class Node
{
  public final int level;
  public final String label;
  
  Node(int level, String label)
  {
    super();
    this.level = level;
    this.label = label;
  }
  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((label == null) ? 0 : label.hashCode());
    result = prime * result + level;
    return result;
  }
  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Node other = (Node) obj;
    if (label == null)
    {
      if (other.label != null)
        return false;
    } else if (!label.equals(other.label))
      return false;
    if (level != other.level)
      return false;
    return true;
  }
  @Override
  public String toString()
  {
    return "Level" + level + ":" + label + "]";
  }
}