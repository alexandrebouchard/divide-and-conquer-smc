package prototype.io;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import briefj.BriefIO;
import briefj.collections.Counter;
import briefj.opt.Option;
import briefj.run.Mains;
import briefj.run.Results;



public class PreprocessNYSchoolData implements Runnable
{
  @Option(required=true) public File inputFile;
  
  /**
   * Starting in 2010, NYSED changed the scale score required to meet each of the proficiency levels, 
   * increasing the number of questions students needed to answer correctly to meet proficiency.
   * 
   * https://data.cityofnewyork.us/Education/NYS-Math-Test-Results-By-Grade-2006-2011-School-Le/jufi-gzgp
   */
  @Option public HashSet<Integer> exludedYears = Sets.newHashSet(2010, 2011);
  
  /**
   * District 75 provides citywide educational, vocational, and behavior support programs for 
   * students who are on the autism spectrum, have significant cognitive delays, are severely 
   * emotionally challenged, sensory impaired and/or multiply disabled. 
   * 
   * http://schools.nyc.gov/Academics/SpecialEducation/D75/AboutD75/default.htm
   */
  @Option public HashSet<Integer> excludeDistricts = Sets.newHashSet(75);
  
  @Option(gloss="Set to -1 to do no filtering") public int gradeFilter = 3;
  
  /**
   * @param args
   */
  public static void main(String[] args)
  {
    Mains.instrumentedRun(args, new PreprocessNYSchoolData());
  }

  @Override
  public void run()
  {
    PrintWriter out = BriefIO.output(Results.getFileInResultFolder("preprocessedNYSData.csv"));
    
    int totalNLine = 0;
    Counter<String> errors = new Counter<String>();
    loop:for (Map<String,String> line : BriefIO.readLines(inputFile).indexCSV())
      try
      {
        totalNLine++;
        String DBN = line.get("DBN");
        if (DBN.length() != 6)
          throw new RuntimeException();
        int district = Integer.parseInt(DBN.substring(0,2));
        if (excludeDistricts.contains(district))
          continue loop;
        
        String county = counties.get(DBN.substring(2, 3)).toString();
        int school = Integer.parseInt(DBN.substring(3, 6));
        int grade = Integer.parseInt(line.get("Grade"));
        if (gradeFilter != -1 && gradeFilter != grade)
          continue loop;
        
        int year = Integer.parseInt(line.get("Year"));
        if (exludedYears.contains(year))
          continue loop;
        
        int numberTrials = Integer.parseInt(line.get("Number Tested"));
        int level34 = Integer.parseInt(line.get("Level 3+4 #"));
        out.println(Joiner.on(",").join("NY", county, district, school, year, numberTrials, level34));
      }
      catch (Exception e)
      {
        errors.incrementCount("" + e, 1.0);
      }
    
    System.out.println("There were errors in " + ((int) errors.totalCount()) + " out of " + totalNLine + " lines");
    System.out.println();
    System.out.println("Count\tError");
    for (String key : errors.keySet())
      System.out.println("" + errors.getCount(key) + "\t" + key);
    
    out.close();
  }

  private static Map<String,String> counties; {
    counties = Maps.newLinkedHashMap();
    counties.put("M", "Manhattan");
    counties.put("X", "Bronx");
    counties.put("K", "Kings");
    counties.put("Q", "Queens");
    counties.put("R", "Richmond");
  }

}
