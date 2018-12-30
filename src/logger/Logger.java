package logger;

import java.io.*;

public class Logger {
    private static String fileName; // Logger file
    private static final String endl = "\n";
    private static final String errorTag = "Error: "; // Error tag for output

    /**
     * Setting logger file
     * @param logName
     */
    public static void setLogFile (String logName) {
        fileName = logName;
    }

    /**
     * Write some data in logger file
     * @param data
     */
    public static void writeLn (String data) {
        PrintStream logWriter;
        try {
           if (fileName == null) {
              logWriter = System.out;
           } else {
              logWriter = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName, true)));
           }

           logWriter.println(data);
           if (fileName != null)
               logWriter.close();
        } catch (IOException ex) {
            System.out.println("logger.logger Error!");
            System.out.println(ex);
        }
    }

    /**
     * Write error string in logger file
     * @param error
     */
    public static void writeErrorLn (String error) {
        writeLn(errorTag + error);
    }

    /**
     * Write IOException error in logger file
     * @param error
     */
    public static void writeErrorLn (IOException error) {
        writeErrorLn(error.getMessage());
    }
}
