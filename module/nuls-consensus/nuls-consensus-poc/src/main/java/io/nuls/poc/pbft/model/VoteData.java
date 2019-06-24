package io.nuls.poc.pbft.model;

import io.nuls.base.data.NulsHash;

/**
 * @author Niels
 */
public class VoteData {
    private long height;

    private int round;

    private NulsHash hash;

    private byte[] address;

    public boolean isBifurcation() {
        return bifurcation;
    }

    public void setBifurcation(boolean bifurcation) {
        this.bifurcation = bifurcation;
    }

    private boolean bifurcation;

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public NulsHash getHash() {
        return hash;
    }

    public void setHash(NulsHash hash) {
        this.hash = hash;
    }

    public byte[] getAddress() {
        return address;
    }

    public void setAddress(byte[] address) {
        this.address = address;
    }
}
