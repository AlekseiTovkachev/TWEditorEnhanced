package app.tweditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DBList extends DBElementValue
  implements Cloneable, Iterable<DBElement>
{
  private List<DBElement> elementList;
  private Map<String, DBElement> labelMap;
  private final AppEnvironment environment;

  public DBList(AppEnvironment environment, int capacity)
  {
    this.environment = environment;
    this.elementList = new ArrayList<>(capacity);
    this.labelMap = new HashMap<>(capacity);
  }

  public boolean addElement(DBElement element)
  {
    String label = element.getLabel();
    if (label.length() == 0) {
      this.elementList.add(element);
      return true;
    }

    if (this.labelMap.get(label) != null) {
      return false;
    }
    this.elementList.add(element);
    this.labelMap.put(label, element);
    return true;
  }

  public boolean insertElement(int index,DBElement element) {
    String label = element.getLabel();
    if (label.length() == 0) {
      this.elementList.add(index, element);
      return true;
    }

    if (this.labelMap.get(label) != null) {
      return false;
    }
    this.elementList.add(index, element);
    this.labelMap.put(label, element);
    return true;
  }

  public DBElement removeElement(int index)
  {
    DBElement element = this.elementList.get(index);
    this.elementList.remove(index);
    String label = element.getLabel();
    if (label.length() != 0) {
      this.labelMap.remove(label);
    }
    return element;
  }

  public boolean removeElement(String label)
  {
    if ((label == null) || (label.length() == 0)) {
      throw new IllegalArgumentException("No database element label supplied");
    }
    DBElement element = this.labelMap.get(label);
    if (element == null) {
      return false;
    }
    boolean removed = this.elementList.remove(element);
    if (removed) {
      this.labelMap.remove(label);
    }
    return removed;
  }

  public boolean removeElement(DBElement element)
  {
    boolean removed = this.elementList.remove(element);
    if (removed) {
      String label = element.getLabel();
      if (label.length() != 0) {
        this.labelMap.remove(label);
      }
    }
    return removed;
  }

  public DBElement getElement(String label)
  {
    if ((label == null) || (label.length() == 0)) {
      throw new IllegalArgumentException("No database element label supplied");
    }
    return this.labelMap.get(label);
  }

  public void setElement(String label, DBElement element)
  {
    if ((label == null) || (label.length() == 0)) {
      throw new IllegalArgumentException("No database element label supplied");
    }
    DBElement oldElement = this.labelMap.get(label);
    if (oldElement != null) {
      int index = this.elementList.indexOf(oldElement);
      this.elementList.set(index, element);
    } else {
      this.elementList.add(element);
    }

    this.labelMap.put(label, element);
  }

  public int getElementCount()
  {
    return this.elementList.size();
  }

  public Iterator<DBElement> iterator()
  {
    return this.elementList.iterator();
  }

  public DBElement getElement(int index)
  {
    return this.elementList.get(index);
  }

  public void setElement(int index, DBElement element)
  {
    DBElement oldElement = this.elementList.get(index);
    String oldLabel = oldElement.getLabel();
    String label = element.getLabel();
    if (!label.equals(oldLabel)) {
      throw new IllegalArgumentException("New label is not the same as old label");
    }
    this.elementList.set(index, element);
    this.labelMap.put(label, element);
  }

  public String getString(String label)
    throws DBException
  {
    DBElement element = getElement(label);
    String value;
    if (element != null) {
      int fieldType = element.getType();
      if (fieldType == 10) {
        value = (String)element.getValue();
      }
      else
      {
        if (fieldType == 11) {
          value = (String)element.getValue();
        }
        else
        {
          if (fieldType == 12) {
            LocalizedString string = (LocalizedString)element.getValue();
            if (string.getSubstringCount() > 0) {
              LocalizedSubstring substring = string.getSubstring(this.environment.getLanguageID(), 0);
              if (substring != null)
                value = substring.getString();
              else
                value = string.getSubstring(0).getString();
            } else {
              int refid = string.getStringReference();
              if (refid >= 0)
                value = this.environment.getString(refid);
              else
                value = "";
            }
          } else {
            throw new DBException("Field " + label + " is not a string");
          }
        }
      } } else { value = ""; }


    return value;
  }

  public void setString(String label, String value)
    throws DBException
  {
    DBElement element = getElement(label);
    if (element != null) {
      int fieldType = element.getType();
      if (fieldType == 10) {
        element.setValue(value);
      } else if (fieldType == 11) {
        element.setValue(value);
      } else if (fieldType == 12) {
        LocalizedString string = (LocalizedString)element.getValue();
        LocalizedSubstring substring = new LocalizedSubstring(value, this.environment.getLanguageID(), 0);
        string.addSubstring(substring);
      } else {
        throw new DBException("Field " + label + " is not a string");
      }
    } else {
      addElement(new DBElement(10, 0, label, value));
    }
  }

  public int getInteger(String label)
    throws DBException
  {
    DBElement element = getElement(label);
    int value;
    if (element != null) {
      int fieldType = element.getType();
      if ((fieldType == 0) || (fieldType == 2) || (fieldType == 3) || (fieldType == 5))
      {
        value = ((Integer)element.getValue()).intValue();
      }
      else
      {
        if ((fieldType == 6) || (fieldType == 7) || (fieldType == 4)) {
          value = ((Long)element.getValue()).intValue();
        }
        else
        {
          if (fieldType == 1) {
            value = ((Character)element.getValue()).charValue();
          }
          else
          {
            if (fieldType == 8) {
              value = ((Float)element.getValue()).intValue();
            }
            else
            {
              if (fieldType == 9)
                value = ((Double)element.getValue()).intValue();
              else
                throw new DBException("Field " + label + " is not numeric");  } 
          }
        }
      } } else { value = 0; }


    return value;
  }

  public void setInteger(String label, int value)
    throws DBException
  {
    setInteger(label, value, 5);
  }

  public void setInteger(String label, int value, int type)
    throws DBException
  {
    DBElement element = getElement(label);
    if (element != null) {
      int fieldType = element.getType();
      if (fieldType == 0) {
        element.setValue(value & 0xFF);
      } else if (fieldType == 2) {
        element.setValue(value & 0xFFFF);
      } else if (fieldType == 3) {
        int shortValue = value & 0xFFFF;
        if (shortValue > 32767)
          shortValue |= -65536;
        element.setValue(shortValue);
      } else if (fieldType == 5) {
        element.setValue(value);
      } else if (fieldType == 4) {
        element.setValue(Long.valueOf(value & 0xFFFFFFFF));
      } else if ((fieldType == 6) || (fieldType == 7)) {
        element.setValue(Long.valueOf(value));
      } else if (fieldType == 1) {
        element.setValue(Character.valueOf((char)value));
      } else if (fieldType == 8) {
        element.setValue(Float.valueOf(value));
      } else if (fieldType == 9) {
        element.setValue(Double.valueOf(value));
      } else {
        throw new DBException("Field " + label + " is not numeric");
      }
    } else {
      addElement(new DBElement(type, 0, label, value));
    }
  }

  public float getFloat(String label)
    throws DBException
  {
    DBElement element = getElement(label);
    float value;
    if (element != null) {
      int fieldType = element.getType();
      if (fieldType == 8)
        value = ((Float)element.getValue()).floatValue();
      else
        throw new DBException("Field " + label + " is not floating-point");
    }
    else {
      value = 0.0F;
    }

    return value;
  }

  public void setFloat(String label, float value)
    throws DBException
  {
    DBElement element = getElement(label);
    if (element != null) {
      int fieldType = element.getType();
      if (fieldType == 8)
        element.setValue(Float.valueOf(value));
      else
        throw new DBException("Field " + label + " is not floating-point");
    }
    else {
      addElement(new DBElement(8, 0, label, Float.valueOf(value)));
    }
  }

  public DBList clone()
  {
    DBList clonedList = (DBList)super.clone();

    int count = this.elementList.size();
    clonedList.elementList = new ArrayList<>(count);
    clonedList.labelMap = new HashMap<>(count);
    for (DBElement element : this.elementList) {
      clonedList.addElement(element.clone());
    }
    return clonedList;
  }
}

