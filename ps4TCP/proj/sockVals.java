/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2016</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Vicky Tu
 * @version 1.0
 */

    public class sockVals{
        public int lAddr = -1;
        public int lPort = -1;
        public int rAddr = -1;
        public int rPort = -1;
        public int seqNum = 0;

        public sockVals(int lAddr, int lPort, int rAddr, int rPort, int seqNum){
            this.lAddr = lAddr;
            this.lPort = lPort;
            this.rAddr = rAddr;
            this.rPort = rPort;
            this.seqNum = seqNum;
        }

    }