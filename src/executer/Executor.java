package executer;

import adapter.AdapterType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Executor interface
 */
public interface Executor {
    /**
     * Set consumer to executor
     * @param consumer
     * @throws IOException
     */
    void setConsumer(Executor consumer) throws IOException;

    /**
     * Set adapter to executor
     * @param provider
     * @param adapter
     * @param typeOfAdapter
     */
    void setAdapter(Executor provider, Object adapter, AdapterType typeOfAdapter);

    /**
     * Get readable types from consumer
     * @return ArrayList<AdapterType>: readable types
     */
    ArrayList<AdapterType> getReadableTypes();

    /**
     * Set config file to executor
     * @param configFile
     * @throws IOException
     */
    void setConfigFile(String configFile) throws IOException;

    /**
     * Set output stream
     * @param output
     */
    void setOutput(DataOutputStream output);

    /**
     * Set input stream
     * @param input
     */
    void setInput(DataInputStream input);

    /**
     * Run function
     */
    void run() throws IOException;

    /**
     * Transfer data to consumers
     * @param provider
     * @throws IOException
     */
    void put(Executor provider) throws IOException;
}
