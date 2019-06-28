package io.nuls.poc.pbft.model;

import io.nuls.base.data.NulsHash;
import io.nuls.poc.model.bo.round.MeetingMember;
import io.nuls.poc.model.bo.round.MeetingRound;

/**
 * @author Niels
 */
public class ProduceKey {

    private byte[] address;

    private long startTime;
    private long endTime;

    private long height;

    private NulsHash prehash;
    private int indexOfRound;


    private MeetingMember self;
    private MeetingRound round;

    public byte[] getAddress() {
        return address;
    }

    public void setAddress(byte[] address) {
        this.address = address;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public NulsHash getPrehash() {
        return prehash;
    }

    public void setPrehash(NulsHash prehash) {
        this.prehash = prehash;
    }

    public int getIndexOfRound() {
        return indexOfRound;
    }

    public void setIndexOfRound(int indexOfRound) {
        this.indexOfRound = indexOfRound;
    }

    public MeetingMember getSelf() {
        return self;
    }

    public void setSelf(MeetingMember self) {
        this.self = self;
    }

    public MeetingRound getRound() {
        return round;
    }

    public void setRound(MeetingRound round) {
        this.round = round;
    }
}
