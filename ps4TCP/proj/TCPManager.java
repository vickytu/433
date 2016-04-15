/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

import java.util.*;
import java.lang.Integer;

public class TCPManager {
    public Node node;
    public int addr;
    private Manager manager;

    private HashMap<String, TCPSock> socks;
    private int[] ports; 

    private static final byte dummy[] = new byte[0];

    private final double ALPHA = 0.125;
    private final double BETA = 0.25;


    /* TCPManager constructor */
    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
    }

    /**
     * Start this TCP manager
     */
    public void start() {
        /* create Hashmap */
        this.socks = new HashMap<String, TCPSock>();
        this.ports = new int[256];
        //this.node.logError("TCPManager started");
    }

    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
        TCPSock sock = new TCPSock(this);
        //this.node.logError("new socket created");
        return sock;
    }

    /* determines what happens given a packet of a spceific type */
    public void receiveTCP(Packet pkt) {
        /* parse header */
        int sa = pkt.getSrc();
        int da = pkt.getDest();
        byte[] payload = pkt.getPayload();
        Transport packet = Transport.unpack(payload);
        int sp = packet.getSrcPort();
        int dp = packet.getDestPort();
        int seqNum = packet.getSeqNum();
        /* direct to right socket 
            search on 4 attributes; if failed,
            search on 2 attributes*/
        String key = "" + da + "," + dp + "," + sa + "," + sp;
        //this.node.logError("in receiveTCP, got pkt FROM: " + da + "," + dp + " TO:" + sa + "," + sp);
        TCPSock sock = this.socks.get(key);
        if (sock == null){
            key = "" + da + "," + dp + ",-1,-1";
            sock = this.socks.get(key);
            if (sock == null){
                //this.node.logError("this packet has bad destAddr and destPort");
                return;
            }
        }
        //this.node.logError("got sock w key: " + key + " base: " + sock.base + " seqNum: " + sock.seqNum + " #things in windowBuf: " + sock.windowBuf.size() + " windowSize: " + sock.ackWindow);

        switch(packet.getType()) {

            case Transport.SYN:
                System.out.print("S");
                //this.node.logError("MSG: SYN w/ seq Num " + seqNum);
                /* do not spawn new socket */
                /* if space in accept queue, add sockVals w 4 attributes to accept queue */ 
                if (sock.pending.size() < sock.backlog){
                    sockVals pend = new sockVals(da, dp, sa, sp, seqNum); 
                    if (!sock.pending.contains(pend)){
                        //this.node.logError("adding " + da + "," + dp + "," + sa + "," + sp + ", w seqNum " + seqNum + " to pending queue");
                        sock.pending.add(pend);
                    }
                }
                break;

            case Transport.ACK:
                //this.node.logError("MSG: ACK w/ seq Num " + seqNum);

                if (sock.state == TCPSock.State.LISTEN){
                    //this.node.logError("in receiveTCP, listening socket throws away ACK");
                    return;
                }
                /* always trust the server. If the ACK has a seqNum y, remove any packet w seqNum less than y */
                if (seqNum > sock.base){
                    System.out.print(":");
                    sock.dup = 0;
                    if (sock.isConnectionPending()){
                        /* this is a SYN ACK, not a DATA ACK. Set the dest addr and port of the write socket */
                        sock.state = TCPSock.State.ESTABLISHED;
                        sock.remoteAddr = sa;
                        sock.remotePort = sp;             
                        sock = this.socks.remove(key);
                        String nKey = "" + da + "," + dp + "," + sa + "," + sp;
                        //this.node.logError("setting my client socket to " + nKey);
                        socks.put(nKey, sock);
                        markUsed(sp);
                        sock.base = seqNum;
                    }else {
                        /* this is a DATA ACK */
                        sock.window = packet.getWindow();
                        Card card;
                        long ackTime = this.manager.now();

                        while (seqNum > sock.base){

                            /* remove any packet w lower seqNum */
                            if (!sock.windowBuf.isEmpty()){
                                card = sock.windowBuf.removeFirst();
                            }else{
                                card = sock.transBuf.removeFirst();
                            }

                            int len = card.tpkt.getPayload().length;
                            //this.node.logError("seqNum: " + seqNum + " - len: " + len + " sock.base: " + sock.base);

                            if (card.dup == false && (sock.base == seqNum-len)){
                                /* calculate RTT on any non dup packets */
                                sock.samRTT = ackTime-card.time;
                                //System.out.format("ackTime: %d sendTime: %d%n", ackTime, card.time);
                                sock.estRTT = (long)(0.875*sock.estRTT) + (long)(0.125*sock.samRTT);
                                //System.out.format("estRTT: %d%n", sock.estRTT);
                                sock.devRTT = (long)(0.75*sock.devRTT) + (long)(0.25*Math.abs((long)(sock.samRTT-sock.estRTT)));
                                //System.out.format("devRTT: %d%n", sock.devRTT);
                                sock.timeoutTime = sock.estRTT + (long) (4*sock.devRTT);
                                //System.out.format("Setting timeoutTime to %d%n", sock.timeoutTime);
                            }

                            sock.base += len;
                            sock.counter++;
                        }
                        while (sock.counter >= sock.ackWindow){
                            /* AIMD */
                            sock.counter -= sock.ackWindow;
                            sock.ackWindow++;
                        }

                        while (sock.ackWindow - sock.windowBuf.size() > 0 && !sock.transBuf.isEmpty()){
                            /* send enough to fill up the ackWindow size */
                            sendNext(sock);
                        }
                    
                        if ((sock.transBuf == null) && sock.isClosurePending()){
                            /* this was a FIN ACK. close the write socket */
                            sock.release();
                        }
                    }
                }else if (seqNum == sock.base){

                    System.out.print("?");
                    sock.dup++;
                    if (sock.dup == 3){
                        /* Triple Dup ACK */
                        int currW = sock.ackWindow/2;
                        if (!sock.windowBuf.isEmpty()){
                            Card card = sock.windowBuf.getFirst();
                            Transport tpkt = card.tpkt;
                            String[] paramTypes = {"TCPSock", "java.lang.Integer", "java.lang.Integer"};
                            Object[] objs = {sock, tpkt.getSeqNum(), currW};
                            /* resend immediately */
                            this.node.addTimer(0, "timeout", paramTypes, objs, sock);
                            //this.node.logError("Pkt " + tpkt.getSeqNum() + " got Triple Dup Acked; resending now!!!!");
                        } 
                        /* cut window size in half (redundant yes but I want you to see it) */
                        sock.ackWindow = currW;
                    }
                }
                break;

            case Transport.FIN:
                /* got a FIN, send ACK and remove socket */

                System.out.print("F");
                //this.node.logError("MSG: FIN w/ seq Num " + seqNum);

                if (sock.state == TCPSock.State.LISTEN){
                    //this.node.logError("in receiveTCP, listening socket sends FIN");
                    this.sendSAF(da, dp, sa, sp, Transport.FIN, sock.seqNum, sock.window);
                    sock.seqNum++;
                    return;
                }
                sock.seqNum++;
                this.sendSAF(da, dp, sa, sp, Transport.ACK, sock.seqNum, sock.window);
                sock.state = TCPSock.State.CLOSED;
    
                while (!sock.windowBuf.isEmpty()){
                    sock.windowBuf.removeFirst();
                }
                while (!sock.transBuf.isEmpty()){
                    sock.transBuf.removeFirst();
                }
                this.rmSock(sock);
                break;

            case Transport.DATA:
                
                //this.node.logError("MSG: DATA w/ seqNum " + seqNum);

                if (sock.state == TCPSock.State.LISTEN){
                    //this.node.logError("in receiveTCP, listening socket sends FIN");
                    this.sendSAF(da, dp, sa, sp, Transport.FIN, sock.seqNum, sock.window);
                    sock.seqNum++; /* not necessary but sure */
                    return;
                }
                
                if (seqNum==sock.seqNum){
                    /* got the DATA I expect */
                    System.out.print(".");
                    byte[] load = packet.getPayload();
                    int len = load.length;
                    if(sock.processInput(load) > 0){
                        /* send ACK */
                        sock.seqNum += len;
                        sock.base += len;
                        this.sendSAF(da, dp, sa, sp, Transport.ACK, sock.seqNum, sock.window);
                        System.out.print(":");
                    }
                }else{
                    if (seqNum < sock.seqNum){
                        /* got the DATA I've seen before */
                        System.out.print("!");
                    }else{
                        /* got the DATA I need later, not now */
                        System.out.print("&");
                    }
                    // send another ACK with the last seq num, since the client probably dropped it
                    this.sendSAF(da, dp, sa, sp, Transport.ACK, sock.seqNum, sock.window);
                    System.out.print("?");
                }
                
                break;
            }
    }

    /* function called by TCPSock to register itself w TCPManager */
    public void hashSock(TCPSock sock, String sockKey) {
        this.socks.put(sockKey, sock);
    }


    /* 0 is not used, 1 is used */
    public int checkUsed(int localPort) {
        /* check if local port is already in the HashMap */
        if (this.ports[localPort] == 0){
            /* the port is not in use */
            return 0;
        }
        return 1;
    }

    /* mark localPort as used to that TCPManager doesn't try to make two sockets with exact same address and port */
    public void markUsed(int localPort){
        this.ports[localPort] = 1;
    }

    /* deregistering the socket requires removing it from the HashMap and freeing its port*/
    public void rmSock(TCPSock sock){
        String key = "" + sock.localAddr + "," + sock.localPort + "," + sock.remoteAddr + "," + sock.remotePort;
        //this.node.logError("in rmSock, removing " + key);
        socks.remove(key);
        this.ports[sock.localPort] = 0;
        /* remove any trace of sock */
    }

    /* used to send a DATA or Fin Pkt. Takes things off transBuf, send them, put them on windowBuf */
    public void sendNext(TCPSock sock) {
        if (sock.transBuf.isEmpty()){
            return;
        }
        
        Card curr = sock.transBuf.removeFirst();
        Transport tpkt = curr.tpkt;
        if (tpkt.getType() == Transport.DATA){
            System.out.print(".");
        }else{
            System.out.print("F");
        }
        //this.node.logError("sending DATA from Node " + sock.localAddr + " to Node " + sock.remoteAddr + " w seqNum " + tpkt.getSeqNum());
        byte[] payload = tpkt.pack();
        curr.time = this.manager.now();
        this.node.sendSegment(sock.localAddr, sock.remoteAddr, Protocol.TRANSPORT_PKT, payload);
        //this.node.logError("Pkt " + tpkt.getSeqNum() + " added to end of windowBuf");
        sock.windowBuf.addLast(curr);

        /* sets timer for reliable transport */
        String[] paramTypes = {"TCPSock", "java.lang.Integer", "java.lang.Integer"};
        Object[] objs = {sock, tpkt.getSeqNum(), 1};
        this.node.addTimer(sock.timeoutTime, "timeout", paramTypes, objs, sock);
        //this.node.logError("Pkt " + tpkt.getSeqNum() + " will time out after " + sock.timeoutTime + " ms");
    }

    /* used to send a SYN, ACK, or FIN immediately */
    public void sendSAF(int sa, int sp, int da, int dp, int type, int seqNum, int window){
        String command;
        if (type == Transport.SYN){
            command = "SYN";
            System.out.print("S");
        }else if (type == Transport.ACK){
            command = "ACK";
        }else{
            command = "FIN";
            System.out.print("F");
        }
        //this.node.logError("sending " + command + " from Node " + sa + " to Node " + da + " w seqNum " + seqNum + " and window " + window);
        Transport tpkt = new Transport(sp, dp, type, window, seqNum, dummy);
        byte[] payload = tpkt.pack();
        this.node.sendSegment(sa, da, Protocol.TRANSPORT_PKT, payload);
    }

    /*
     * End Socket API
     */
}
