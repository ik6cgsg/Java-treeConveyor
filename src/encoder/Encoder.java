package encoder;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import adapter.AdapterType;
import adapter.ByteAdapter;
import adapter.DoubleAdapter;
import javafx.util.Pair;
import logger.Logger;
import executer.Executor;

public class Encoder implements Executor {
  private static final String splitDelim = " |:|="; // Delimiter in config file
  private static final String delim = " ";          // Delimiter in table of probabilities file
  private static final String endl = "\n";
  private DataInputStream inputFile;
  private String inputFileName;
  private DataOutputStream outputFile;
  private String tableFile;
  private Map<Byte, Double> probability;            // Map of letters to their probabilities
  private Map<Byte, Segment> segs;                  // Map of letters to their segments
  private int textLen, numSeq, blockSize, dataLen;
  private ArrayList<Executor> consumers;            // Array of all consumers
  private Map<Executor, Pair<Object, AdapterType>> adapters; // // Map of providers to pair of their adapter and adapter type
  private Object dataOut;

  private enum targetType {
    ENCODE,
    DECODE
  }

  private enum tableMethodType {
    READ,   // read probabilities from table
    WRITE   // set probabilities to table file
  }

  private enum confTypes {
    BLOCK_SIZE,
    SEQUENCE_LEN,
    TEXT_LEN,
    PROBABILITY,
    DECODE_CONF,
    TARGET,
    TABLE_FILE,
    TABLE_METHOD
  }

  private targetType target = targetType.ENCODE;
  private static final Map<String, targetType> tMap;        // Map target name to target type
  private static final Map<String, confTypes> configMap;    // Map config name to config type
  private static final Map<String, tableMethodType> metMap; // Map table method name to table method type
  private ArrayList<AdapterType> readableTypes;             // Array of current readable types
  private ArrayList<AdapterType> writableTypes;             // Array of current writable types

  class DoubleAdapterClass implements DoubleAdapter {
    int index = 0;  // current index in dataOut

    /**
     * Get next double from dataOut
     * @return Double: next double if exists, null otherwise
     */
    @Override
    public Double getNextDouble() {
      if (index == ((ArrayList<Double>) dataOut).size()) {
        index = 0;
        return null;
      }
      return ((ArrayList<Double>) dataOut).get(index++);
    }
  }

  class ByteAdapterClass implements ByteAdapter {
    int index = 0;  // current index in dataOut

    /**
     * Get next byte from dataOut
     * @return Byte: next byte if exists, null otherwise
     */
    @Override
    public Byte getNextByte() {
      if (index == ((ArrayList<Byte>) dataOut).size()) {
        index = 0;
        return null;
      }
      return ((ArrayList<Byte>) dataOut).get(index++);
    }
  }

  static {
    tMap = new HashMap<>();
    tMap.put("encode", targetType.ENCODE);
    tMap.put("decode", targetType.DECODE);

    configMap = new HashMap<>();
    configMap.put("num", confTypes.SEQUENCE_LEN);
    configMap.put("len", confTypes.TEXT_LEN);
    configMap.put("prob", confTypes.PROBABILITY);
    configMap.put("decconf", confTypes.DECODE_CONF);
    configMap.put("target", confTypes.TARGET);
    configMap.put("block", confTypes.BLOCK_SIZE);
    configMap.put("table", confTypes.TABLE_FILE);
    configMap.put("table_method", confTypes.TABLE_METHOD);

    metMap = new HashMap<>();
    metMap.put("read", tableMethodType.READ);
    metMap.put("write", tableMethodType.WRITE);
  }

  /**
   * Encoder constructor
   * @param inFile: input file name for setting probability table
   * @throws IOException
   */
  public Encoder(String inFile) throws IOException {
    probability = new HashMap<>();
    readableTypes = new ArrayList<>();
    writableTypes = new ArrayList<>();
    consumers = new ArrayList<>();
    adapters = new HashMap<>();
    segs = new HashMap<>();
    inputFileName = inFile;
    textLen = 0;
  }

  /**
   * Set encoder configs from file
   * @param confFile
   * @throws IOException
   */
  private void setConfigs(String confFile) throws IOException {

    BufferedReader configReader = new BufferedReader(new FileReader(confFile));
    String line;
    while ((line = configReader.readLine()) != null) {
      String[] words = line.split(splitDelim);
      if (words.length != 2 && words.length != 3)
        throw new IOException("Wrong number of arguments in file: " + confFile + " at: " + line);
      confTypes type = configMap.get(words[0]);
      if (type == null)
        throw new IOException("Unknown config: " + words[0] + " in file: " + confFile + " at: " + line);
      switch (type) {
        case SEQUENCE_LEN: {
          numSeq = Integer.parseInt(words[1]);
          break;
        }
        case TEXT_LEN: {
          textLen = Integer.parseInt(words[1]);
          break;
        }
        case PROBABILITY: {
          byte ch = (byte) Integer.parseInt(words[1]);
          probability.put(ch, Double.parseDouble(words[2]));
          segs.put(ch, new Segment());
          break;
        }
        case TARGET: {
          target = tMap.get(words[1]);
          if (target == null)
            throw new IOException("Unknown target: " + words[1] + " in file: " + confFile + " at: " + line + " decode|encode expected");
          switch (target) {
            case ENCODE: {
              readableTypes.add(AdapterType.BYTE);
              writableTypes.add(AdapterType.DOUBLE);
              break;
            }
            case DECODE: {
              writableTypes.add(AdapterType.BYTE);
              readableTypes.add(AdapterType.DOUBLE);
              break;
            }
          }
          break;
        }
        case BLOCK_SIZE: {
          blockSize = Integer.parseInt(words[1]);
          break;
        }
        case TABLE_FILE: {
          tableFile = words[1];
          break;
        }
        case TABLE_METHOD: {
          tableMethodType tm = metMap.get(words[1]);
          if (tm == null)
            throw new IOException("Unknown method: " + words[1] + "in file: " + confFile + " at: " + line + " read|write expected");
          switch (tm) {
            case READ: {
              setConfigs(tableFile);
              break;
            }
            case WRITE: {
              countProb();
              writeDecodeConf();
            }
          }
          break;
        }
      }
    }
    configReader.close();
    Logger.writeLn("Configs have been set");
  }

  /**
   * Count probabilities of letters in input file
   * @throws IOException
   */
  private void countProb() throws IOException {
    DataInputStream copy = new DataInputStream(new FileInputStream(inputFileName));
    while (copy.available() > 0) {
      byte ch = copy.readByte();
      textLen++;
      if (!probability.containsKey(ch))
        probability.put(ch, 1.0);
      else
        probability.replace(ch, probability.get(ch) + 1);

      segs.putIfAbsent(ch, new Segment());
    }

    copy.close();

    for (Byte key : probability.keySet())
      probability.replace(key, probability.get(key) / textLen);
    Logger.writeLn("Probability have been counted");
  }

  /**
   * Set segments of letters
   */
  private void defineSegments() {
    double l = 0;

    for (Map.Entry<Byte, Segment> entry : segs.entrySet()) {
      entry.getValue().left = l;
      entry.getValue().right = l + probability.get(entry.getKey());
      l = entry.getValue().right;
    }
  }

  /**
   * Write table file
   * @throws IOException
   */
  private void writeDecodeConf() throws IOException {
    BufferedWriter encWriter = new BufferedWriter(new FileWriter(tableFile));

    for (Map.Entry<String, confTypes> entry : configMap.entrySet()) {
      switch (entry.getValue()) {
        case SEQUENCE_LEN: {
          encWriter.write(entry.getKey() + delim + numSeq + endl);
          break;
        }
        case PROBABILITY: {
          for (Map.Entry<Byte, Double> prEntry : probability.entrySet()) {
            encWriter.write(entry.getKey() + delim + prEntry.getKey() + delim + prEntry.getValue() + endl);
          }
          break;
        }
      }
    }
    encWriter.close();
  }

  /**
   * Code data
   * @param data
   * @return Object: coded data
   */
  public Object code(Object data) {
    switch (target) {
      case ENCODE: {
        try {
          return encode((ArrayList<Byte>) data);
        } catch (IOException ex) {
          Logger.writeLn("Encoding Error!");
          Logger.writeErrorLn(ex);
          System.exit(1);
        }
        break;
      }
      case DECODE: {
        try {
          return decode((ArrayList<Double>) data);
        } catch (IOException ex) {
          Logger.writeLn("Decoding Error!");
          Logger.writeErrorLn(ex);
          System.exit(1);
        }
        break;
      }
    }
    return null;
  }

  /**
   * Encode bytes array
   * @param data
   * @return ArrayList<Double>: array of encoded double
   * @throws IOException
   */
  private ArrayList<Double> encode(ArrayList<Byte> data) throws IOException {
    Logger.writeLn("Encoding...");
    defineSegments();

    int size = (int) Math.ceil((double) data.size() / numSeq);
    ArrayList<Double> newData = new ArrayList<>();

    for (int i = 0; i < size; i++) {
      double left = 0, right = 1;
      for (int j = 0; j < numSeq; j++) {
        if (i * numSeq + j >= dataLen)
          break;
        byte ch = data.get(i * numSeq + j);
        double newR = left + (right - left) * segs.get(ch).right;
        double newL = left + (right - left) * segs.get(ch).left;
        right = newR;
        left = newL;
      }
      newData.add((left + right) / 2);
    }
    Logger.writeLn("Encoding finished!!");
    return newData;
  }

  /**
   * Decode from array of doubles
   * @param data
   * @return ArrayList<Byte>: decoded byte array
   * @throws IOException
   */
  private ArrayList<Byte> decode(ArrayList<Double> data) throws IOException {
    Logger.writeLn("Decoding...");
    defineSegments();

    ArrayList<Byte> newData = new ArrayList<>(numSeq * data.size());

    for (int i = 0; i < data.size(); i++) {
      double code = data.get(i);
      for (int j = 0; j < numSeq; j++) {
        for (Map.Entry<Byte, Segment> entry : segs.entrySet())
          if (code >= entry.getValue().left && code < entry.getValue().right) {
            newData.add(numSeq * i + j, entry.getKey());
            code = (code - entry.getValue().left) / (entry.getValue().right - entry.getValue().left);
            break;
          }
      }
    }
    Logger.writeLn("Decoding finished!!!");
    return newData;
  }

  /**
   * Set config file to executor
   * @param configFile
   * @throws IOException
   */
  @Override
  public void setConfigFile(String configFile) throws IOException {
    setConfigs(configFile);
  }

  /**
   * Set consumer to executor
   * @param consumer
   * @throws IOException
   */
  @Override
  public void setConsumer(Executor consumer) throws IOException {
    consumers.add(consumer);
    boolean canCommunicate = false;

    for (adapter.AdapterType type : consumer.getReadableTypes()) {
      if (writableTypes.contains(type)) {
        canCommunicate = true;
        switch (type) {
          case BYTE: {
            consumer.setAdapter(this, new ByteAdapterClass(), AdapterType.BYTE);
            break;
          }
          case DOUBLE: {
            consumer.setAdapter(this, new DoubleAdapterClass(), AdapterType.DOUBLE);
            break;
          }
        }
      }
    }

    if (!canCommunicate) {
      throw new IOException("Can't communicate, wrong transporter structure");
    }
  }

  /**
   * Set adapter to executor
   * @param provider
   * @param adapter
   * @param typeOfAdapter
   */
  @Override
  public void setAdapter(Executor provider, Object adapter, AdapterType typeOfAdapter) {
    adapters.put(provider, new Pair<>(adapter, typeOfAdapter));
  }

  /**
   * Get readable types from consumer
   * @return ArrayList<AdapterType>: readable types
   */
  @Override
  public ArrayList<AdapterType> getReadableTypes() {
    return readableTypes;
  }

  /**
   * Set output stream
   * @param output
   */
  @Override
  public void setOutput(DataOutputStream output) {
    outputFile = output;
  }

  /**
   * Set input stream
   * @param input
   */
  @Override
  public void setInput(DataInputStream input) {
    inputFile = input;
  }

  /**
   * Run function
   */
  @Override
  public void run() throws IOException {
    while (inputFile.available() > 0) {
      byte[] data = new byte[blockSize];
      if (inputFile.available() > blockSize)
        dataLen = blockSize;
      else
        dataLen = inputFile.available();
      inputFile.read(data, 0, dataLen);
      ArrayList<Byte> bdata = new ArrayList<>();
      for (int i = 0; i < data.length; i++)
        bdata.add(data[i]);
      dataOut = code(bdata);
      if (!consumers.isEmpty()) {
        for (Executor consumer : consumers)
          consumer.put(this);
      } else {
        outputFile.write((byte[]) dataOut);
      }
    }
  }

  /**
   * Transfer data to consumers
   * @param provider
   * @throws IOException
   */
  @Override
  public void put(Executor provider) throws IOException {
    Object currentAdapter = adapters.get(provider).getKey();
    AdapterType type = adapters.get(provider).getValue();

    switch (type) {
      case BYTE: {
        ArrayList<Byte> bdata = new ArrayList<>();
        ByteAdapterClass byteAdapter = (ByteAdapterClass) currentAdapter;
        Byte cur;

        while ((cur = byteAdapter.getNextByte()) != null) {
          bdata.add(cur);
          // reading by blocks
          while ((cur = byteAdapter.getNextByte()) != null && bdata.size() < blockSize) {
            bdata.add(cur);
          }
          dataLen = bdata.size();
          dataOut = code(bdata);
          if (!consumers.isEmpty()) {
            for (Executor consumer : consumers)
              consumer.put(this);
          } else {
            for (int i = 0; i < ((ArrayList<Double>) dataOut).size(); i++) {
              outputFile.writeDouble(((ArrayList<Double>) dataOut).get(i).doubleValue());
            }
          }
          if (cur == null)
            break;
        }
        break;
      }
      case DOUBLE: {
        ArrayList<Double> ddata = new ArrayList<>();
        DoubleAdapterClass doubleAdapter = (DoubleAdapterClass) currentAdapter;
        Double cur;

        while ((cur = doubleAdapter.getNextDouble()) != null) {
          ddata.add(cur);
          // reading bu blocks
          while ((cur = doubleAdapter.getNextDouble()) != null && ddata.size() < blockSize) {
            ddata.add(cur);
          }
          dataLen = ddata.size();
          dataOut = code(ddata);
          if (!consumers.isEmpty()) {
            for (Executor consumer : consumers)
              consumer.put(this);
          } else {
            for (int i = 0; i < ((ArrayList<Byte>) dataOut).size(); i++) {
              outputFile.write(((ArrayList<Byte>) dataOut).get(i).byteValue());
            }
          }
          if (cur == null)
            break;
        }
        break;
      }
      default: {
        throw new IOException("Cant work with type: " + type);
      }
    }
  }
}
