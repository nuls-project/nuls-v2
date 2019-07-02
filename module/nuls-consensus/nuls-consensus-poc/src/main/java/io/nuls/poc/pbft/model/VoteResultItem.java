package io.nuls.poc.pbft.model;

import io.nuls.base.data.NulsHash;

/**
 * @author Niels
 */
public class VoteResultItem {

    private NulsHash hash;

    private int count;

    private long roundIndex;
    private int currentMemberIndex;
    private long roundStartTime;

    public long getRoundIndex() {
        return roundIndex;
    }

    public void setRoundIndex(long roundIndex) {
        this.roundIndex = roundIndex;
    }

    public int getCurrentMemberIndex() {
        return currentMemberIndex;
    }

    public void setCurrentMemberIndex(int currentMemberIndex) {
        this.currentMemberIndex = currentMemberIndex;
    }

    public long getRoundStartTime() {
        return roundStartTime;
    }

    public void setRoundStartTime(long roundStartTime) {
        this.roundStartTime = roundStartTime;
    }

    public NulsHash getHash() {
        return hash;
    }

    public void setHash(NulsHash hash) {
        this.hash = hash;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
