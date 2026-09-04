package app.tweditor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextDatabase
{
  private List<String> columns;
  private Map<String, Integer> columnMap;
  private List<String[]> resources;

  public TextDatabase(String filePath)
    throws DBException, IOException
  {
    try (BufferedReader in = new BufferedReader(new FileReader(filePath))) {
      readDefinitions(in);
    }
  }

  public TextDatabase(File file)
    throws DBException, IOException
  {
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      readDefinitions(in);
    }
  }

  public TextDatabase(InputStream inputStream)
    throws DBException, IOException
  {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
      readDefinitions(in);
    }
  }

  private void readDefinitions(BufferedReader in)
    throws DBException, IOException
  {
    this.columns = new ArrayList<>(16);
    this.columnMap = new HashMap<>(16);
    this.resources = new ArrayList<>(100);
    boolean headerDone = false;
    boolean columnsDone = false;

    String[] values = null;
    String line;
    while ((line = in.readLine()) != null) {
      int lineLength = line.length();
      if ((lineLength != 0) && (line.charAt(0) != '#'))
      {
        boolean skipIndex = true;
        int index = 0;
        int value = 0;
        if (columnsDone) {
          values = new String[this.columns.size()];
        }

        while (index < lineLength)
        {
          if (Character.isWhitespace(line.charAt(index))) {
            index++;
          }
          else
          {
            boolean quoted;
            if (line.charAt(index) == '"') {
              quoted = true;
              index++;
            } else {
              quoted = false;
            }

            int start = index;
            if (start >= lineLength) {
              break;
            }
            while ((index < lineLength) &&
              (quoted ?
              line.charAt(index) != '"' :
              !Character.isWhitespace(line.charAt(index))))
            {
              index++;
            }
            String token;
            if (start == index)
              token = "";
            else {
              token = line.substring(start, index);
            }
            if ((index < lineLength) && (line.charAt(index) == '"')) {
              index++;
            }

            if (!headerDone) {
              if (value == 0) {
                if (!token.equals("2DA"))
                  throw new DBException("File format '" + token + "' is not supported");
              } else if ((value == 1) &&
                (!token.equals("V2.0")))
                throw new DBException("File version '" + token + "' is not supported");
            }
            else if (!columnsDone) {
              this.columnMap.put(token.toLowerCase(), value);
              this.columns.add(token);
            } else if (skipIndex) {
              skipIndex = false;
              value--;
            } else if (value < values.length) {
              values[value] = token;
            }

            value++;
          }

        }

        if (value > 0) {
          if (columnsDone)
            this.resources.add(values);
          else if (headerDone)
            columnsDone = true;
          else {
            headerDone = true;
          }
        }
      }

    }
  }

  public List<String> getColumnLabels()
  {
    return this.columns;
  }

  public int getResourceCount()
  {
    return this.resources.size();
  }

  public String getString(int resourceIndex, int valueIndex)
  {
    if (resourceIndex >= this.resources.size()) {
      throw new IllegalArgumentException("Resource index is not valid");
    }
    if (valueIndex >= this.columns.size()) {
      throw new IllegalArgumentException("Value index is not valid");
    }
    return this.resources.get(resourceIndex)[valueIndex];
  }

  public String getString(int resourceIndex, String valueLabel)
  {
    if (resourceIndex >= this.resources.size()) {
      throw new IllegalArgumentException("Resource index is not valid");
    }
    Integer valueIndex = this.columnMap.get(valueLabel.toLowerCase());
    if (valueIndex == null) {
      return "";
    }
    String string = this.resources.get(resourceIndex)[valueIndex];
    if ((string.length() >= 4) && (string.substring(0, 4).equals("****"))) {
      string = "";
    }
    return string;
  }

  public int getInteger(int resourceIndex, String valueLabel)
  {
    if (resourceIndex >= this.resources.size()) {
      throw new IllegalArgumentException("Resource index is not valid");
    }
    Integer valueIndex = this.columnMap.get(valueLabel.toLowerCase());
    if (valueIndex == null) {
      return 0;
    }

    String string = this.resources.get(resourceIndex)[valueIndex];
    int value;
    if ((string.length() >= 4) && (string.substring(0, 4).equals("****")))
      value = 0;
    else {
      value = Integer.parseInt(string);
    }
    return value;
  }
}
