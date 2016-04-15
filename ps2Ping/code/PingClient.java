/*
PingClient.java by Vicky Tu
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PingClient {

  private static final int WAIT_DELAY = 1000;
  private static final String header = "PING";
  private static final byte[] ping = header.getBytes(StandardCharsets.US_ASCII);
  private static final String footer = "\r\n";
  private static final byte[] clrf = footer.getBytes(StandardCharsets.US_ASCII);

  static private class MyTimerTask extends TimerTask {
    int j = 0;
    int maxRTT = 0;
    int minRTT = 1000;
    int totRTT = 0;
    int lost = 0;

    //private DatagramSocket clientSocket;
    private Timer timer;
    private InetAddress host;
    private int port;
    private byte[] passwd;
    private int length;

    public MyTimerTask(Timer timer, InetAddress host, int port, byte[] passwd, int length){
      this.timer = timer;
      this.host = host;
      this.port = port;
      this.passwd = passwd;
      this.length = length;
    }

    @Override
    public void run(){
      if (j == 10){
        int aveRTT = totRTT/j;
        double lossRate = ((double)lost)/j;
        if (lossRate == 1.0){
          maxRTT = -1;
          minRTT = -1;
          aveRTT = -1;
        }
        System.out.printf("MaxRTT: %d MinRTT: %d AveRTT: %d LossRate: %f Goodbye!\n", maxRTT, minRTT, aveRTT, lossRate);
        this.cancel();
        timer.cancel();
        return;
      }
      try{
          DatagramSocket clientSocket = new DatagramSocket();
          clientSocket.setSoTimeout(WAIT_DELAY);
          

        byte[] pingNum = new byte[2];
        byte[] sendTime = new byte[8];

        // construct and send datagram

        pingNum[0] = (byte)((j >> 8) & 0xFF); //converting int to a short
        pingNum[1] = (byte)((j >> 0) & 0xFF);
        int len = 16 + length;
        ByteBuffer buf = ByteBuffer.allocate(len);
        buf.put(ping);
        buf.put(pingNum);
        long currentTime = System.currentTimeMillis();
        for (int i = 7; i >= 0; i--) {
          sendTime[i] = (byte) (currentTime % 256);
          currentTime /= 256;
        }
        buf.put(sendTime);
        buf.put(passwd);
        buf.put(clrf);
        byte[] sendData = buf.array();
        
        DatagramPacket sendPacket = new DatagramPacket(sendData, 
                   sendData.length, 
                   host, 
                   port);
        long sentTime = System.currentTimeMillis();
        clientSocket.send(sendPacket);
        buf.clear();
        j++;
        
          byte[] receiveData = new byte [1024];
          DatagramPacket receivePacket = new DatagramPacket(receiveData, 
                receiveData.length);
    
          clientSocket.receive(receivePacket);
      
            long receivedTime = System.currentTimeMillis();
            long RTT = receivedTime - sentTime;
            if ((int) RTT > maxRTT){
              maxRTT = (int) RTT;
            }

            if ((int) RTT < minRTT){
              minRTT = (int) RTT;
            }
            totRTT = totRTT + (int) RTT;
            // print output
            String sentenceFromServer = new String(receivePacket.getData());
            System.out.println("From Server: " + sentenceFromServer + RTT + "ms");
            clientSocket.close();
          }catch(Exception e){
            System.out.println("PingServer reply lost in network");
            lost++;
        }  
    }
  }
  public static void main(String[] args) throws Exception {

    // get server address
    // Get command line argument.
    if (args.length != 3) {
      System.out.println("Required arguments: host port passwd");
      return;
    }

    InetAddress host = InetAddress.getByName(args[0]);
    int port = Integer.parseInt(args[1]);
    int length = args[2].length();
    byte[] passwd = args[2].getBytes(StandardCharsets.US_ASCII);
    
    Timer timer = new Timer();
    MyTimerTask tasknew = new MyTimerTask(timer, host, port, passwd, length);
    // scheduling the task at fixed rate delay
    timer.scheduleAtFixedRate(tasknew, 0, 1000); 
  
    } // end of main

} // end of PingClient