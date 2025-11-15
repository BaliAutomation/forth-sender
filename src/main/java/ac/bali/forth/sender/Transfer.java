package ac.bali.forth.sender;

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

public class Transfer {
    public static final boolean USING_COLORS = false;

    public static final String ANSI_RESET = USING_COLORS ? "\u001B[0m" : "";
    //    public static final String ANSI_BLACK = USING_COLORS ? "\u001B[30m" : "";
    public static final String ANSI_RED = USING_COLORS ? "\u001B[31m" : "";
    public static final String ANSI_GREEN = USING_COLORS ? "\u001B[32m" : "";
//    public static final String ANSI_YELLOW = USING_COLORS ? "\u001B[33m" : "";
//    public static final String ANSI_BLUE = USING_COLORS ? "\u001B[34m" : "";
//    public static final String ANSI_MAGENTA = USING_COLORS ? "\u001B[35m" : "";
    public static final String ANSI_CYAN = USING_COLORS ? "\u001B[36m" : "";
    //    public static final String ANSI_WHITE = USING_COLORS ? "\u001B[37m" : "";
//    public static final String ANSI_DARK_GREY = USING_COLORS ? "\u001B[90m" : "";
//    public static final String ANSI_BRIGHT_RED = USING_COLORS ? "\u001B[91m" : "";
//    public static final String ANSI_BRIGHT_GREEN = USING_COLORS ? "\u001B[92m" : "";
//    public static final String ANSI_BRIGHT_YELLOW = USING_COLORS ? "\u001B[93m" : "";
//    public static final String ANSI_BRIGHT_BLUE = USING_COLORS ? "\u001B[94m" : "";
//    public static final String ANSI_BRIGHT_MAGENTA = USING_COLORS ? "\u001B[95m" : "";
//    public static final String ANSI_BRIGHT_CYAN = USING_COLORS ? "\u001B[96m" : "";
//    public static final String ANSI_BRIGHT_WHITE = USING_COLORS ? "\u001B[97m" : "";
    public static final String OK_RESPONSE = "  " + ANSI_CYAN + "ok." + ANSI_RESET;

    public static final byte[] CR = new byte[]{13};
    public static final String COMPILER_ERROR = "Compiler Error";
    private static SerialPort commPort;

    private static Transfer instance;
//    private int here_ram0;
//    private int here_ram1;
//    private final int here_flash0 = 0x08028000;
//    private int here_flash1;

    public static void main(String[] args)
            throws Exception {

        instance = new Transfer();
        parseCmdLine(args, cmd -> {
            try {
                if (cmd.startsWith(".include ")) {
                    String[] parts = cmd.split(" ");
                    for (int i = 1; i < parts.length; i++)
                        instance.transfer(parts[i]);
                } else {
                    instance.sendLine(cmd);
                }
            } catch (Exception e) {
                System.out.println();
                System.err.println();
                commPort.closePort();
                if (!e.getMessage().equals(COMPILER_ERROR)) {
                    throw new UndeclaredThrowableException(e);
                }
            } finally {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    // ignore
                }
            }
        });
    }

    public Transfer()
            throws Exception {

        String serialPort = "/dev/ttyACM0";
        commPort = SerialPort.getCommPort(serialPort);
        commPort.setBaudRate(115200);
        commPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 2500, 2500);
        commPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);
        if (!commPort.openPort(500)) {
            System.err.println("Unable to open serial port " + serialPort);
            System.exit(1);
        }
//        int here = here();
//        if (here > 0x2000_0000)
//            here_ram0 = here;
    }

    public static void parseCmdLine(String[] args, Consumer<String> then) throws IOException {
        String file = args[0];
        if (file.endsWith(".fload")) {
            List<String> lines = Files.readAllLines(Paths.get(file));
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("\\ "))      // trim comment lines and don't send to target
                {
                    System.out.println(line);    // but print it so we know where we are
                } else {
                    then.accept(line);
                }
            }
        } else {
            then.accept(".include " + file);
        }
    }

    public void transfer(String file) {
        try {
            System.out.println(ANSI_GREEN + file + ANSI_RESET);
            System.out.println();

            List<String> lines = Files.readAllLines(Paths.get(file));
            for (String line : lines) {
                sendLine(line);
            }
//            int here = here();
//            if( here > 0x20000000) {
//                System.out.println("Size:" + (here - here_ram1) + "  (Total (ram): " + (here - here_ram0) + ")    (Free (ram): " + (0x20010000 - here) + ")");
//                here_ram1 = here;
//            }else {
//                System.out.println("Size:" + (here - here_flash1) + "  (Total (flash): " + (here - here_flash0) + ")    (Free (flash): " + (0x0803F000 - here) + ")");
//                here_flash1 = here;
//            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendLine(String line) {
        writeLine(commPort, line);
        String echo = read(commPort);
        System.out.println(echo);

        int matchesUpTo = match(line, echo);
        if (matchesUpTo != line.length() || !endsWithOk(echo)) {
            if( echo.equals(OK_RESPONSE)) {
                echo = read(commPort);      // This is needed to support the \\\ feeding source code into the Forth system. It will return an extra "ok."
                matchesUpTo = match(line, echo);
                if (matchesUpTo == line.length() && endsWithOk(echo)) {
                    return;
                }
            }
            System.out.println();
            System.out.println("Matches up to: " + matchesUpTo);
            System.out.println("    Sent:  " + line + "    Length(" + line.length() +")");
            System.out.println("Received:  " + echo + "    Length(" + echo.length() +")");
            throw new RuntimeException(COMPILER_ERROR);
        }
    }

//    private int here() throws InterruptedException {
//        drain();
//        writeLine(commPort, "hex here .");
//        String line = read(commPort);
//        String[] parts = line.split(" ");
//        return Integer.parseInt(parts[3], 16);
//    }

    private void sendCr() {
        commPort.writeBytes(CR, 1);
    }

    private void drain()
            throws InterruptedException {
        System.out.println("Draining...");
        sendCr();
        sendCr();
        sendCr();
        Thread.sleep(100);
        byte[] buffer = new byte[2048];
        while (commPort.bytesAvailable() > 0) {
            commPort.readBytes(buffer, 1);
            System.out.print(new String(buffer, 0, 1));
            //noinspection BusyWait
            Thread.sleep(1);
        }
        System.out.println("\nDraining Done");
    }

    private void writeLine(SerialPort commPort, String line) {
        byte[] bytes = line.getBytes();
        int bytesWritten = commPort.writeBytes(bytes, bytes.length);
        if (bytesWritten != bytes.length) {
            System.err.println(ANSI_RED + "Couldn't write all of:" + line + ANSI_RESET);
            throw new RuntimeException("Write timeout");
        }
        sendCr();
    }

    private String read(SerialPort commPort) {
        byte[] buffer = new byte[1];
        StringBuilder result = new StringBuilder();
        while (true) {
            if (commPort.readBytes(buffer, 1) > 0) {
                char c = (char) buffer[0];
                if ((c == 13 || c == 10)) {
                    if (result.length() > 0)
                        return result.toString();
                } else {
                    result.append(c);
                }

            } else {
                throw new RuntimeException("Read Timeout");
            }
        }
    }

    private int match(String sent, String received) {
        if (received.startsWith(sent) && endsWithOk(received)) {
            return sent.length();
        }
        byte[] buf1 = sent.getBytes();
        byte[] buf2 = received.getBytes();
        int length = received.length();
        for (int i = 0; i < length - 1; i++) {
            if (i == buf1.length)
                return i;
            if (buf1[i] != buf2[i]) {
                return i;
            }
        }
        return length;
    }

    private boolean endsWithOk(String text) {
        if (text.endsWith("ok." + ANSI_RESET))
            return true;
        return text.endsWith("ok'" + ANSI_RESET);
    }
}
