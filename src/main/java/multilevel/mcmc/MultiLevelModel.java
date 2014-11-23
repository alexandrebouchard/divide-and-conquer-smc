package multilevel.mcmc;

import multilevel.io.MultiLevelDataset;
import blang.annotations.DefineFactor;



public class MultiLevelModel
{
  @DefineFactor
  public final MultiLevelBMTreeFactor multiLevelBMTreeFactor;
  
  public MultiLevelModel(MultiLevelDataset data)
  {
    multiLevelBMTreeFactor = new MultiLevelBMTreeFactor(null, data, data.getRoot());
  }
}
