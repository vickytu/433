/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

import java.util.*;
import java.nio.ByteBuffer;
import java.lang.Integer;

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }

    private static final byte dummy[] = new byte[0]; 

    public State state;

    public Deque<Card> transBuf;
    public Deque<Card> windowBuf;
    private ByteBuffer byteBuf;

    public int window;

    public int base;
    public int seqNum;
    public int finNum;

    public int localAddr = -1;
    public int localPort = -1;
    public int remoteAddr = -1;
    public int remotePort = -1;

    public int ackWindow;
    public int counter;
    public int dup;

    public long estRTT = 1000;
    public long samRTT = 1000;
    public long devRTT = 0;
    public long timeoutTime = 1000;

    public int backlog;

    public Queue<sockVals> pending;

    private TCPManager tcpMan;

    /* TCPSock Constructor */
    public TCPSock(TCPManager tcpMan) {
        this.tcpMan = tcpMan;
        this.localAddr = tcpMan.addr;
        this.window = 1096;
        this.ackWindow = 1;
        this.counter = 0;
        this.dup = 0;
        this.transBuf = new LinkedList<Card>();
        this.windowBuf = new LinkedList<Card>();
        this.byteBuf = ByteBuffer.allocate(this.window);
        this.finNum = -1;
    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        /* check if local port already used 
            tcpMan.checkUsed()
            if (=0), tcpMan.addPort() */
        //tcpMan.node.logError("Bind socket to " + localPort);
        int used = tcpMan.checkUsed(localPort);
        if (used == 0){
            this.localPort = localPort;
            tcpMan.markUsed(localPort);
            String key = "" + localAddr + "," + localPort + ",-1,-1";
            tcpMan.hashSock(this, key);
            //tcpMan.node.logError("port " + localPort + " has been bound");
            return 0;
        }else {
            System.out.println("Port already in use");
            return -1;
        }
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        /* set state to listen */
        this.state = State.LISTEN;
        this.backlog = backlog;
        this.pending = new LinkedList<sockVals>();
        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        /* if accept queue empty
            return -1
            else create the socket from sockVals on top of accept queue and hash it */
        //tcpMan.node.logError("Accept pending connection");

        if (pending.size() == 0){
            //tcpMan.node.logError("in accept, pending queue is empty");
            return null;
        }
        TCPSock nSock = new TCPSock(this.tcpMan);
        sockVals next = this.pending.remove();
        nSock.localPort = next.lPort;
        nSock.remoteAddr = next.rAddr;
        nSock.remotePort = next.rPort;
        nSock.base = next.seqNum;
        nSock.seqNum = next.seqNum+1;

        String key = "" + next.lAddr + "," + next.lPort + "," + next.rAddr + "," + next.rPort;
        //tcpMan.node.logError("spawned sock " + key);
        tcpMan.hashSock(nSock, key);
        tcpMan.sendSAF(nSock.localAddr, nSock.localPort, nSock.remoteAddr, nSock.remotePort, Transport.ACK, nSock.seqNum, nSock.window);
        return nSock;
    }

    /* has SYN been sent? */
    public boolean isConnectionPending() {
        return (state == State.SYN_SENT);
    }

    /* has the socket been closed? */
    public boolean isClosed() {
        return (state == State.CLOSED);
    }

    /* has SYN ACK happened? */
    public boolean isConnected() {
        return (state == State.ESTABLISHED);
    }

    /* is the socket in graceful shutdown? */
    public boolean isClosurePending() {
        return (state == State.SHUTDOWN);
    }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        /* send syn to DA DP with LA LP*/
        if (this.isConnected()){
            //tcpMan.node.logError("socket" + this.localAddr + this.localPort + this.remoteAddr + this.remotePort + " is already connected");
            return 0;
        }
        Random rand = new Random(); 
        this.base = rand.nextInt(50); 
        this.seqNum = this.base;
        tcpMan.sendSAF(this.localAddr, this.localPort, destAddr, destPort, Transport.SYN, this.seqNum, this.window);
        this.seqNum++;
        this.state = State.SYN_SENT;
        return -1;
        /* change state */
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
        if(this.isClosed()){
            return;
        }
        /* send FIN */
        //tcpMan.node.logError("close the socket");
        this.state = State.SHUTDOWN;
        /* check if buffer is empty; if is
                release()
            if not
                return */
        if (transBuf.isEmpty() && windowBuf.isEmpty()){
            this.release();
        }else{

            Transport tpkt = new Transport(localPort, remotePort, Transport.FIN, this.window, this.seqNum, dummy);
            Card finCard = new Card(tpkt, 0);
            this.seqNum++;
            //tcpMan.node.logError("FIN packet  w/ seqNum " + tpkt.getSeqNum() + "added to transBuf");
            this.transBuf.add(finCard);
        }
        return;
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        if(this.isClosed()){
            return;
        }
        //tcpMan.node.logError("release the socket");
        this.state = State.CLOSED;
        byteBuf.clear();
        tcpMan.sendSAF(this.localAddr, this.localPort, this.remoteAddr, this.remotePort, Transport.FIN, this.seqNum, this.window);
        this.seqNum++;
        while (!windowBuf.isEmpty()){
            windowBuf.removeFirst();
        }
        while (!transBuf.isEmpty()){
            transBuf.removeFirst();
        }
        tcpMan.rmSock(this);
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        //tcpMan.node.logError("Writing from socket to buf");
        Transport tpkt;
        Card card;
        byte[] payload;
        int bytesWritten = 0;

        if (this.isClosed()){
            //tcpMan.node.logError("in write, error b/c connection is closed");
            return -1;
        }

        if (this.isClosurePending()){ 
            //tcpMan.node.logError("in write, can't write b/c connection is closing");
            return 0;
        }

        /* flow control as to not overhelm a slow receiver */
        if (len > this.window){
            len = this.window;
            //tcpMan.node.logError("SET LEN TO " + this.window);
        }

        while (len > Transport.MAX_PAYLOAD_SIZE){
            payload = new byte[Transport.MAX_PAYLOAD_SIZE];
            System.arraycopy(buf, pos, payload, 0, Transport.MAX_PAYLOAD_SIZE);
            tpkt = new Transport(localPort, remotePort, Transport.DATA, this.window, seqNum, payload);
            card = new Card(tpkt, 0);
            pos = pos + Transport.MAX_PAYLOAD_SIZE;
            len = len - Transport.MAX_PAYLOAD_SIZE;
            bytesWritten += Transport.MAX_PAYLOAD_SIZE;
            seqNum = seqNum + payload.length;
            //tcpMan.node.logError("Pkt " + tpkt.getSeqNum() + " added to end of transBuf");
            this.transBuf.addLast(card);
        }
        payload = new byte[len];
        System.arraycopy(buf, pos, payload, 0, len);
        tpkt = new Transport(localPort, remotePort, Transport.DATA, this.window, this.seqNum, payload);
        card = new Card(tpkt, 0);
        bytesWritten += len;
        pos = pos + len;
        len = len - len;
        seqNum = seqNum + payload.length;
        //tcpMan.node.logError("Pkt " + tpkt.getSeqNum() + " added to end of transBuf");
        this.transBuf.addLast(card);

        /* only write as much to fill up the ackWindow size */
        while((this.ackWindow - this.windowBuf.size() > 0) && !this.transBuf.isEmpty()){
            tcpMan.sendNext(this);
            //tcpMan.node.logError("# of things in windowBuf: " + this.windowBuf.size());
        }
        return bytesWritten;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        /* check if socket closed or shutdown
            check pos valid
            check len */
        if (this.isClosed() || pos < 0){
            return -1;
        }
        this.byteBuf.flip();
        int remaining = byteBuf.remaining();
        //tcpMan.node.logError("Reading from socket to byteBuf");
        if (len < remaining){
            /* read was what requested */
            //tcpMan.node.logError("read " + len + " bytes = len");
            this.byteBuf.get(buf, pos, len);
            this.byteBuf.compact();
            this.window = this.byteBuf.remaining();
            return len;
        }else{
            /* read as much as I have */
            //tcpMan.node.logError("read" + remaining + " bytes = remaining");
            this.byteBuf.get(buf, pos, remaining);
            this.byteBuf.compact();
            this.window = this.byteBuf.remaining();
            return remaining;
        }
        /* if len < Blen
            read len
            return len
            len >= Blen
            read Blen
            return Blen */
    }

    public int processInput(byte[] payload) {
        //tcpMan.node.logError("Putting payload in byteBuf");
        
        if (this.byteBuf.remaining() > payload.length){
            /* add payload to buffer */
            //tcpMan.node.logError("position before adding payload into buffer: " + this.byteBuf.position());
            this.byteBuf.put(payload);
            this.window = this.byteBuf.remaining();
            //tcpMan.node.logError("position after adding payload into buffer: " + this.byteBuf.position());
            return payload.length;
        }
        /* pretend to drop the packet */
        //tcpMan.node.logError("payload len: " + payload.length + ". Not enough space in byteBuf. Pretend like pkt lost");
        return 0;
    }

    /* timeout function called that changes ackWindow size and resends ackWindow number of packets */
    public void timeout(TCPSock sock, Integer sNum, Integer ackW) {
        //tcpMan.node.logError("timedout: Pkt " + sNum);
        if (!sock.isClosed() && base == sNum){
            while (!windowBuf.isEmpty()){
                transBuf.addFirst(windowBuf.removeLast());
                transBuf.getFirst().dup = true;
            }
            
            this.ackWindow = ackW;

            for (int i = 0; i<this.ackWindow; i++){
                System.out.print("!");
                tcpMan.sendNext(sock);    
            }
        }
    }


    /**/

    /*
     * End of socket API
     */
}
