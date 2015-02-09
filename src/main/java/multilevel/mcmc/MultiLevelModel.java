package multilevel.mcmc;

import multilevel.io.MultiLevelDataset;
import multilevel.mcmc.MultiLevelBMTreeFactor.Initialization;
import multilevel.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;
import blang.annotations.DefineFactor;



public class MultiLevelModel
{
  @DefineFactor
  public final MultiLevelBMTreeFactor multiLevelBMTreeFactor;
  
  public MultiLevelModel(MultiLevelDataset data, MultiLevelModelOptions options, Initialization init)
  {
    multiLevelBMTreeFactor = new MultiLevelBMTreeFactor(null, data, data.getRoot(), options, init);
  }
}
