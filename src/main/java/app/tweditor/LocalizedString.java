package app.tweditor;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class LocalizedString extends DBElementValue
  implements Cloneable
{
  private int stringReference;
  private List<LocalizedSubstring> substringList;

  public LocalizedString(int reference)
  {
    this.stringReference = reference;
    this.substringList = new ArrayList<>(4);
  }

  public void addSubstring(LocalizedSubstring substring)
  {
    int language = substring.getLanguage();
    int gender = substring.getGender();
    ListIterator<LocalizedSubstring> li = this.substringList.listIterator();
    boolean found = false;
    while (li.hasNext()) {
      LocalizedSubstring oldSubstring = li.next();
      if ((oldSubstring.getLanguage() == language) && (oldSubstring.getGender() == gender)) {
        li.set(substring);
        found = true;
        break;
      }
    }

    if (!found)
      this.substringList.add(substring);
  }

  public int getStringReference()
  {
    return this.stringReference;
  }

  public void setStringReference(int reference)
  {
    this.stringReference = reference;
  }

  public int getSubstringCount()
  {
    return this.substringList.size();
  }

  public LocalizedSubstring getSubstring(int index)
  {
    return this.substringList.get(index);
  }

  public List<LocalizedSubstring> getSubstrings()
  {
    return this.substringList;
  }

  public void setSubstring(int index, LocalizedSubstring substring)
  {
    this.substringList.set(index, substring);
  }

  public LocalizedSubstring getSubstring(int language, int gender)
  {
    LocalizedSubstring value = null;
    for (LocalizedSubstring substring : this.substringList) {
      if ((substring.getLanguage() == language) && (substring.getGender() == gender)) {
        value = substring;
        break;
      }
    }

    return value;
  }

  public LocalizedString clone()
  {
    LocalizedString clonedString = (LocalizedString)super.clone();

    clonedString.substringList = new ArrayList<>(this.substringList.size());
    for (LocalizedSubstring substring : this.substringList) {
      clonedString.substringList.add(substring.clone());
    }
    return clonedString;
  }
}

