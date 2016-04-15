// PingServer.java
    import java.io.*;
    import java.net.*;
    import java.util.*;
    import java.nio.charset.StandardCharsets;

    /* 
     * Server to process ping requests over UDP.
     */
        
    public class PingServer
    {
       private static double LOSS_RATE = 0.3;
       private static int AVERAGE_DELAY = 100; // milliseconds
       private static final String moreheader = "ECHO";
       private static final byte[] echo = moreheader.getBytes(StandardCharsets.US_ASCII);

       public static void main(String[] args) throws Exception
       {
          int port = 1025;
          String passwd = "";
          if (args.length > 1 && args.length < 5) {
             port = Integer.parseInt(args[0]);
             passwd = args[1];
          }else{
             System.out.println("Required arguments: port passwd");
             return;
          }

          if (args.length > 2){
            AVERAGE_DELAY = Integer.parseInt(args[2]);
          }

          if (args.length > 3){
            LOSS_RATE = Double.parseDouble(args[3]);
          }

          // Create random number generator for use in simulating
          // packet loss and network delay.
          Random random = new Random();

          // Create a datagram socket for receiving and sending
          // UDP packets through the port specified on the
          // command line.
          DatagramSocket socket = new DatagramSocket(port);
          passwd = passwd.concat("\r\n");

          // Processing loop.
          while (true) {

             // Create a datagram packet to hold incomming UDP packet.
             DatagramPacket
                request = new DatagramPacket(new byte[1024], 1024);
      
             // Block until receives a UDP packet.
             socket.receive(request);
         
             // Print the received data, for debugging
             printData(request);

             // Decide whether to reply, or simulate packet loss.
             if (random.nextDouble() < LOSS_RATE) {
                System.out.println(" Reply not sent.");
                continue;
             }

             // Simulate prorogation delay.
             Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));

             // Send reply.
             InetAddress clientHost = request.getAddress();
             int clientPort = request.getPort();
             byte[] clientData = request.getData();
             int len = request.getLength();
             byte[] slice = Arrays.copyOfRange(clientData, 14, len);
             String s = new String(slice);
             if (s.equals(passwd)){
              System.out.print("passwd matched");

              byte[] buf = new byte[clientData.length + echo.length];
              System.arraycopy(clientData, 0, buf, 0, 4);
              System.arraycopy(echo, 0, buf, 4, echo.length);
              System.arraycopy(clientData, 4, buf, echo.length+4, clientData.length-4);

             DatagramPacket
             reply = new DatagramPacket(buf, buf.length, 
                                        clientHost, clientPort);
        
             socket.send(reply);
        
             System.out.println(" Reply sent.");
           }else{
            System.out.println(" No Reply sent.");
           }
         } // end of while
       } // end of main

       /* 
        * Print ping data to the standard output stream.
        */
       private static void printData(DatagramPacket request) 
               throws Exception

       {
          // Obtain references to the packet's array of bytes.
          byte[] buf = request.getData();

          // Wrap the bytes in a byte array input stream,
          // so that you can read the data as a stream of bytes.
          ByteArrayInputStream bais 
              = new ByteArrayInputStream(buf);

          // Wrap the byte array output stream in an input 
          // stream reader, so you can read the data as a
          // stream of **characters**: reader/writer handles 
          // characters
          InputStreamReader isr 
              = new InputStreamReader(bais);

          // Wrap the input stream reader in a bufferred reader,
          // so you can read the character data a line at a time.
          // (A line is a sequence of chars terminated by any 
          // combination of \r and \n.)
          BufferedReader br 
              = new BufferedReader(isr);

          // The message data is contained in a single line, 
          // so read this line.
          String line = br.readLine();

          // Print host address and data received from it.
          System.out.println("Received from " +         
            request.getAddress().getHostAddress() +
            ": " +
            new String(line) );
         } // end of printData
       } // end of class