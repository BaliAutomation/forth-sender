package ac.bali.forth.sender;

import com.fazecast.jSerialComm.SerialPort;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Transfer
{
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";

    public static final byte[] CR = new byte[] { 13 };
    public static final String COMPILER_ERROR = "Compiler Error";

    public static void main(String[] args)
        throws Exception
    {
        String file = args[0];
        String serialPort = "/dev/ttyACM0";
        SerialPort commPort = SerialPort.getCommPort(serialPort);
        commPort.setBaudRate(115200);
        commPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 500, 500);
        commPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);
        if( ! commPort.openPort(500) )
        {
            System.err.println("Unable to open serial port " + serialPort);
            System.exit(1);
        }

        drain(commPort);

        try
        {
            Transfer instance = new Transfer();
            if (file.startsWith("@"))
            {
                List<String> files = Files.readAllLines(Paths.get(file.substring(1)));
                for (String filename : files)
                {
                    instance.transfer(filename, commPort);
                }
            }
            else
            {
                instance.transfer(file, commPort);
            }
        } catch (Exception e)
        {
            System.out.println();
            System.err.println();
            commPort.closePort();
            Thread.sleep(100);
            if( ! e.getMessage().equals(COMPILER_ERROR)) {
                throw e;
            }
        }
    }

    private static void sendCr(SerialPort commPort)
    {
        commPort.writeBytes(CR, 1);
    }

    private static void drain(SerialPort commPort)
            throws InterruptedException
    {
        System.out.println("Draining...");
        sendCr(commPort);
        sendCr(commPort);
        sendCr(commPort);
        Thread.sleep(100);
        byte[] buffer = new byte[2048];
        while (commPort.bytesAvailable() > 0)
        {
            commPort.readBytes(buffer, 1);
            System.out.print(new String(buffer, 0, 1));
            Thread.sleep(1);
        }
        System.out.println("\nDraining Done");
    }

    private void transfer(String file, SerialPort commPort)
        throws Exception
    {
        System.out.println(ANSI_GREEN + file + ANSI_RESET);
        System.out.println();
        List<String> lines = Files.readAllLines(Paths.get(file));
        for (String line : lines)
        {
//            if (line.length() > 0 && !line.startsWith("\\"))
            {
                byte[] bytes = line.getBytes();
                int bytesWritten = commPort.writeBytes(bytes, bytes.length);
                if (bytesWritten != bytes.length)
                {
                    System.err.println("Couldn't write all of:" + line);
                    break;
                }
                commPort.writeBytes(CR, 1);
                String echo = readLine(commPort);
                System.out.print(echo.substring(0,line.length()));
                System.out.print(ANSI_GREEN);
                System.out.print(echo.substring(line.length()));
                System.out.println(ANSI_RESET);

                int matchesUpTo = match((line + "  ok.").getBytes(), echo.getBytes(), bytes.length + 5);
                if (matchesUpTo != bytes.length + 5)
                {
                    System.err.println("Error: " + ANSI_RED + echo.substring(matchesUpTo - 1) + ANSI_RESET);
                    throw new RuntimeException(COMPILER_ERROR);
                }
            }
        }
    }

    private String readLine(SerialPort commPort)
    {
        byte[] buffer = new byte[1];
        StringBuilder result = new StringBuilder();
        while (true)
        {
            if (commPort.readBytes(buffer, 1) == 1)
            {
                char c = (char) buffer[0];
                if (c == 13 || c == 10)
                {
                    return result.toString();
                }
                else
                {
                    result.append(c);
                }

            }
            else
            {
                throw new RuntimeException("Timeout");
            }
        }
    }

    private void waitForBytes(SerialPort commPort, int length)
    {
        long until = System.currentTimeMillis() + 2000;
        while (commPort.bytesAvailable() < length)
        {
            if (System.currentTimeMillis() > until)
            {
                throw new RuntimeException("Timeout");
            }
        }
    }

    int match(byte[] buf1, byte[] buf2, int length)
    {
        for (int i = 0; i < length - 1; i++)
        {
            if (buf1[i] != buf2[i])
            {
                return i;
            }
        }
        return length;
    }
}
