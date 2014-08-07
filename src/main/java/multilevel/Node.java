package multilevel;

import java.util.List;

import briefj.BriefLists;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;



public class Node
{
  public final List<String> path;

  public Node(List<String> path)
  {
    this.path = path;
  }
  
  public Node child(String childName)
  {
    return new Node(BriefLists.concat(this.path, childName));
  }
  
  public static Node root(String name)
  {
    return new Node(Lists.newArrayList(name));
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((path == null) ? 0 : path.hashCode());
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
    if (path == null)
    {
      if (other.path != null)
        return false;
    } else if (!path.equals(other.path))
      return false;
    return true;
  }
  
  @Override
  public final String toString()
  {
    return toString("-");
  }
  
  public final String toString(String separator)
  {
    return Joiner.on(separator).join(path);
  }

  public int level()
  {
    return path.size() - 1;
  }
}