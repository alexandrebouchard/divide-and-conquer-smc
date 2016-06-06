package prototype.mcmc;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bayonet.coda.EffectiveSize;
import briefj.BriefIO;
import briefj.BriefMaps;
import briefj.opt.Option;
import briefj.run.OptionsUtils;



public class ESSMain
{
  @Option(required = true)
  public static File samples;
  
  @Option
  public static ArrayList<String> fields = new ArrayList<>();
  
  public static void main(String [] args)
  {
    try
    {
      OptionsUtils.parseOptions(args, ESSMain.class);
    }
    catch (Exception e)
    {
      return;
    }
    
    Set<String> fieldSet = null;
    
    
    Map<String,List<Double>> allData = new LinkedHashMap<>();
    for (Map<String,String> line : BriefIO.readLines(samples).indexCSV('#'))
    {
      if (fieldSet == null)
        fieldSet = fields.isEmpty() ? line.keySet() :  new LinkedHashSet<>(fields);
      
      for (String fieldName : fieldSet)
        BriefMaps.getOrPutList(allData, fieldName).add(Double.parseDouble(line.get(fieldName)));
    }
    
    for (String key : allData.keySet())
      System.out.println(key + "\t" + EffectiveSize.effectiveSize(allData.get(key)));
    
  }
}
