package prototype.mcmc;

import prototype.io.MultiLevelDataset;
import prototype.mcmc.MultiLevelBMTreeFactor.Initialization;
import prototype.smc.DivideConquerMCAlgorithm.MultiLevelModelOptions;
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
