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

    public static void main(String[] args)
        throws Exception
    {
        String file = args[0];
        SerialPort commPort = SerialPort.getCommPort("/dev/ttyACM0");
        System.out.println("Draining...");
        sendCr(commPort);
        sendCr(commPort);
        sendCr(commPort);
        Thread.sleep(100);
        drain(commPort);
        System.out.println("\nDraining Done");

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
            commPort.closePort();
            throw e;
        }
        Thread.sleep(2000);
    }

    private void transfer(String file, SerialPort commPort)
        throws Exception
    {

        commPort.setBaudRate(115200);
        commPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 500, 500);
        commPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);
        System.out.println(commPort.openPort(500));


        List<String> lines = Files.readAllLines(Paths.get(file));
        for (String line : lines)
        {
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("\\"))
            {
//                System.out.println(" Sending:[" + line + "]");
                byte[] bytes = line.getBytes();
                int bytesWritten = commPort.writeBytes(bytes, bytes.length);
                if (bytesWritten != bytes.length)
                {
                    System.err.println("Couldn't write all of:" + line);
                    break;
                }
                commPort.writeBytes(CR, 1);
                String echo = readLine(commPort);
                System.out.println(echo);

                int matchesUpTo = match((line + "  ok.").getBytes(), echo.getBytes(), bytes.length + 5);
                if (matchesUpTo != bytes.length)
                {
                    System.err.println("Error: " + ANSI_RED + echo.substring(matchesUpTo - 2) + ANSI_RESET);
                    break;
                }
            }
        }
    }

    private static void sendCr(SerialPort commPort)
    {
        commPort.writeBytes(CR, 1);
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

    private static void drain(SerialPort commPort)
        throws InterruptedException
    {
        byte[] buffer = new byte[2048];

        while (commPort.bytesAvailable() > 0)
        {
            commPort.readBytes(buffer, 1);
            System.out.print(new String(buffer, 0, 1));
            Thread.sleep(1);
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
