package bayonet.classif;

import java.io.Serializable;

/**
 * LabeledInstances are input instances along with a label.
 * 
 * @author Dan Klein
 */
public class LabeledInstance<I,L> implements Serializable {
  private I instance;
  private L label;
  
  public static <I,L> LabeledInstance<I,L> create(L label, I instance) 
  {
    return new LabeledInstance<I,L>(label,instance);
  }


  public I getInput() {
    return instance;
  }

  public L getLabel() {
    return label;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LabeledInstance)) return false;

    final LabeledInstance labeledInstance = (LabeledInstance) o;

    if (instance != null ? !instance.equals(labeledInstance.instance) : labeledInstance.instance != null) return false;
    if (label != null ? !label.equals(labeledInstance.label) : labeledInstance.label != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (instance != null ? instance.hashCode() : 0);
    result = 29 * result + (label != null ? label.hashCode() : 0);
    return result;
  }

  public LabeledInstance(L label, I instance) {
    this.label = label;
    this.instance = instance;
  }
  @Override
  public String toString()
  {
    return "[In:" + instance + ",Lab:" + label + "]";
  }
}
