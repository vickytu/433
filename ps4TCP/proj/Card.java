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

    public class Card{
        public Transport tpkt;
        public long time;
        public boolean dup;

        public Card(Transport tpkt, long time){
            this.tpkt = tpkt;
            this.time = time;
            this.dup = false;
        }
    }