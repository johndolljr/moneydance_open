/************************************************************\
 *      Copyright (C) 2008 Reilly Technologies, L.L.C.      *
\************************************************************/

package com.moneydance.modules.features.jpython;

import com.infinitekind.util.*;
import java.util.*;
import java.io.*;

/** Base Resources class (default=english)
*/
public class Resources
  extends ResourceBundle
{

  private StreamTable resourceTable;
  
  // subclasses should override the file name
  public Resources() {
    this("/com/moneydance/modules/features/jpython/english.dict");
  }

  public Resources(String resourceFileName) {
    resourceTable = new StreamTable();
    try {
      InputStream in = getClass().getResourceAsStream(resourceFileName);
      Reader rdr = new InputStreamReader(in, "UTF8");
      resourceTable.readFrom(rdr);
    } catch (Exception e) { 
      System.err.println("Error reading resources: "+e);
      e.printStackTrace(System.err);
    }
    //System.err.println("Resource table: "+resourceTable);
  }

  public Enumeration getKeys() {
    return resourceTable.keys();
  }

  protected Object handleGetObject(String key) 
    throws MissingResourceException
  {
    Object o = resourceTable.get(key);
    if(o==null) {
      return "??"+key+"??";
    } else {
      return o;
    }
  }
}
